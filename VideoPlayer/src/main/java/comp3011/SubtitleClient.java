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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends the original media audio to our localhost subtitle service.
 * Extraction and HTTP run on one background worker; playback never waits for it.
 */
public final class SubtitleClient implements AutoCloseable {
    private final URI endpoint;
    private final HttpClient httpClient;
    private final ExecutorService worker = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("subtitle-request-", 0).factory());

    public SubtitleClient() {
        // Override only for development tests, e.g. a local mock server.
        endpoint = URI.create(System.getProperty("subtitle.server.url",
                "http://localhost:8080/api/v1/subtitles"));
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CompletableFuture<SubtitleTrack> requestSubtitles(File videoFile) {
        return CompletableFuture.supplyAsync(() -> {
            Path audio = null;
            try {
                audio = AudioExtractor.extract(videoFile);
                return uploadAndParse(audio);
            } catch (Exception e) {
                // Keep the failure on the future; VideoPlayerModel handles it
                // without stopping the video or audio playback.
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                if (audio != null) {
                    try {
                        Files.deleteIfExists(audio);
                    } catch (IOException e) {
                        System.err.println("Could not remove temporary audio file: " + e.getMessage());
                    }
                }
            }
        }, worker);
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

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Subtitle server returned HTTP " + response.statusCode());
        }
        return WhisperSubtitleParser.parse(response.body());
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
