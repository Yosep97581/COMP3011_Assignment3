/* COMP3011 Assignment 3: AI-assisted tests for retry and in-session caching. */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;

class SubtitleClientTest {
    private static final String TIMED_RESPONSE = """
            {"words":[{"word":"Hello","start":0.0,"end":0.5},
                       {"word":"world.","start":0.5,"end":1.0}],
             "segments":[{"start":0.0,"end":1.0,"text":"Hello world."}]}
            """;

    @Test
    void retriesTemporaryErrorThenReusesSessionResult() throws Exception {
        AtomicInteger httpRequests = new AtomicInteger();
        AtomicInteger audioExtractions = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/subtitles", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int status = httpRequests.incrementAndGet() == 1 ? 502 : 200;
            byte[] reply = (status == 502 ? "upstream unavailable" : TIMED_RESPONSE)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, reply.length);
            try (var out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        Path media = Files.createTempFile("subtitle-client-test", ".mp4");
        Files.write(media, new byte[] { 1, 2, 3 });
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/subtitles");
            SubtitleClient.AudioSource fakeAudio = file -> {
                audioExtractions.incrementAndGet();
                Path audio = Files.createTempFile("fake-audio", ".m4a");
                Files.write(audio, new byte[] { 4, 5, 6 });
                return audio;
            };
            try (SubtitleClient client = new SubtitleClient(endpoint, fakeAudio)) {
                SubtitleTrack first = client.requestSubtitles(media.toFile())
                        .get(15, TimeUnit.SECONDS);
                SubtitleTrack second = client.requestSubtitles(media.toFile())
                        .get(3, TimeUnit.SECONDS);
                assertEquals("Hello world.", first.textAt(300_000));
                assertSame(first, second);
                assertEquals(2, httpRequests.get()); // 502, then successful retry
                assertEquals(1, audioExtractions.get());
            }
        } finally {
            Files.deleteIfExists(media);
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryBadRequest() throws Exception {
        AtomicInteger httpRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/subtitles", exchange -> {
            exchange.getRequestBody().readAllBytes();
            httpRequests.incrementAndGet();
            byte[] reply = "bad request".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, reply.length);
            try (var out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        Path media = Files.createTempFile("subtitle-client-test", ".mp4");
        Files.write(media, new byte[] { 1 });
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/subtitles");
            try (SubtitleClient client = new SubtitleClient(endpoint, file -> {
                Path audio = Files.createTempFile("fake-audio", ".m4a");
                Files.write(audio, new byte[] { 4 });
                return audio;
            })) {
                assertThrows(ExecutionException.class,
                        () -> client.requestSubtitles(media.toFile()).get(5, TimeUnit.SECONDS));
                assertEquals(1, httpRequests.get());
            }
        } finally {
            Files.deleteIfExists(media);
            server.stop(0);
        }
    }
}
