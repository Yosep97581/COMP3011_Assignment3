/*
 * COMP3011 Assignment 3.
 * Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs audio extraction and subtitle requests separately from JavaFX playback.
 * Successful subtitles can be reused within this player session. Brief server
 * outages are retried without touching the video/audio playback clock.
 */
public final class SubtitleClient implements AutoCloseable {
    private static final long[] RETRY_DELAYS_MS = { 2_000, 4_000, 8_000, 16_000 };
    private static final int SESSION_CACHE_SIZE = 4;

    @FunctionalInterface
    interface AudioSource {
        Path extract(File file) throws Exception;
    }

    private final URI endpoint;
    private final AudioSource audioSource;
    private final HttpClient httpClient;
    private final ExecutorService worker = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("subtitle-request-", 0).factory());
    private final Map<MediaStamp, SubtitleTrack> sessionCache =
            new LinkedHashMap<>(8, 0.75f, true);

    private record MediaStamp(Path absolutePath, long bytes, long modifiedMillis) { }

    public SubtitleClient() {
        // Override only for development or tests, not with the OpenAI endpoint.
        this(URI.create(System.getProperty("subtitle.server.url",
                "http://localhost:8080/api/v1/subtitles")), AudioExtractor::extract);
    }

    // Package-private seam so tests can use synthetic audio and a local HTTP server.
    SubtitleClient(URI endpoint, AudioSource audioSource) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.audioSource = Objects.requireNonNull(audioSource);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CompletableFuture<SubtitleTrack> requestSubtitles(File videoFile) {
        MediaStamp stamp;
        try {
            stamp = describeFile(videoFile);
            SubtitleTrack cached = getCached(stamp);
            if (cached != null) {
                System.out.println("Subtitles: reused this session's cached subtitle track.");
                return CompletableFuture.completedFuture(cached);
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        // Keep the actual task Future so cancellation interrupts extraction,
        // sleeping retries, or the blocking HTTP request. Simply cancelling a
        // CompletableFuture created by supplyAsync does not interrupt its task.
        CompletableFuture<SubtitleTrack> result = new CompletableFuture<>();
        Future<?> task = worker.submit(() -> {
            Path audio = null;
            try {
                audio = audioSource.extract(videoFile);
                SubtitleTrack track = uploadWithRetries(audio);
                // Only a successful response belongs in the session cache.
                putCached(stamp, track);
                result.complete(track);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(new IOException("Subtitle request cancelled.", e));
            } catch (Exception e) {
                // The VideoPlayerModel logs this but continues playback.
                result.completeExceptionally(e);
            } finally {
                if (audio != null) {
                    try {
                        Files.deleteIfExists(audio);
                    } catch (IOException e) {
                        System.err.println("Could not remove temporary audio file: " + e.getMessage());
                    }
                }
            }
        });
        result.whenComplete((track, error) -> {
            if (result.isCancelled()) {
                task.cancel(true);
            }
        });
        return result;
    }

    private SubtitleTrack uploadWithRetries(Path audio) throws Exception {
        for (int attempt = 0; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Subtitle request interrupted.");
            }
            try {
                return uploadAndParse(audio);
            } catch (RetryableSubtitleException e) {
                if (attempt == RETRY_DELAYS_MS.length) {
                    throw e;
                }
                long delay = RETRY_DELAYS_MS[attempt];
                System.err.printf("Subtitles: server unavailable; retrying in %d seconds.%n",
                        delay / 1_000);
                Thread.sleep(delay);
            }
        }
    }

    private SubtitleTrack uploadAndParse(Path audio) throws Exception {
        String boundary = "comp3011-" + UUID.randomUUID();
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"audio.m4a\"\r\n"
                + "Content-Type: audio/mp4\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(header.getBytes(StandardCharsets.UTF_8)),
                        HttpRequest.BodyPublishers.ofFile(audio),
                        HttpRequest.BodyPublishers.ofByteArray(footer.getBytes(StandardCharsets.UTF_8))))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Connection refused/timeouts are temporary; invalid JSON is not.
            throw new RetryableSubtitleException("Cannot reach the subtitle server.", e);
        }
        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new RetryableSubtitleException("Subtitle server returned HTTP " + status);
        }
        if (status != 200) {
            throw new IOException("Subtitle server returned HTTP " + status);
        }
        return WhisperSubtitleParser.parse(response.body());
    }

    private static MediaStamp describeFile(File file) throws IOException {
        Path absolute = file.toPath().toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(absolute, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) {
            throw new IOException("The selected media is not a regular file.");
        }
        return new MediaStamp(absolute, attrs.size(), attrs.lastModifiedTime().toMillis());
    }

    private synchronized SubtitleTrack getCached(MediaStamp key) {
        return sessionCache.get(key);
    }

    private synchronized void putCached(MediaStamp key, SubtitleTrack value) {
        sessionCache.put(key, value);
        if (sessionCache.size() > SESSION_CACHE_SIZE) {
            sessionCache.remove(sessionCache.keySet().iterator().next());
        }
    }

    private static final class RetryableSubtitleException extends IOException {
        private RetryableSubtitleException(String message) { super(message); }
        private RetryableSubtitleException(String message, Throwable cause) { super(message, cause); }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
