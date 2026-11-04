// COMP3011 Assignment 3: AI-assisted regression tests for the subtitle cache.
package comp3011.assignment1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import comp3011.assignment1.model.SubtitleResponse;

class SubtitleResponseCacheTest {
    private static SubtitleResponse response(String text) {
        return new SubtitleResponse(text, 1, List.of(), List.of());
    }

    @Test
    void repeatedAudioLoadsOnlyOnce() throws Exception {
        SubtitleResponseCache cache = new SubtitleResponseCache(3);
        AtomicInteger calls = new AtomicInteger();
        var first = cache.getOrLoad("same-audio", () -> {
            calls.incrementAndGet();
            return response("Hello");
        });
        var second = cache.getOrLoad("same-audio", () -> {
            calls.incrementAndGet();
            return response("Wrong result");
        });
        assertEquals("Hello", second.text());
        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void failedTranscriptionsAreNotCached() throws Exception {
        SubtitleResponseCache cache = new SubtitleResponseCache(2);
        assertThrows(IOException.class, () -> cache.getOrLoad("audio", () -> {
            throw new IOException("Network unavailable");
        }));
        assertEquals("Recovered", cache.getOrLoad("audio", () -> response("Recovered")).text());
    }

    @Test
    void evictsOldEntriesWhenFull() throws Exception {
        SubtitleResponseCache cache = new SubtitleResponseCache(2);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad("a", () -> response("A"));
        cache.getOrLoad("b", () -> response("B"));
        cache.getOrLoad("a", () -> response("Should not be called"));
        cache.getOrLoad("c", () -> response("C"));
        cache.getOrLoad("b", () -> {
            loads.incrementAndGet();
            return response("B again");
        });
        assertEquals(1, loads.get());
    }

    @Test
    void simultaneousRequestsShareOneTranscription() throws Exception {
        SubtitleResponseCache cache = new SubtitleResponseCache(2);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.getOrLoad("audio", () -> {
                calls.incrementAndGet();
                firstStarted.countDown();
                try {
                    if (!finishFirst.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("Test timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                return response("Shared");
            }));
            assertTrue(firstStarted.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.getOrLoad("audio", () -> {
                calls.incrementAndGet();
                return response("Duplicate");
            }));
            finishFirst.countDown();
            assertEquals("Shared", first.get(3, TimeUnit.SECONDS).text());
            assertEquals("Shared", second.get(3, TimeUnit.SECONDS).text());
            assertEquals(1, calls.get());
        }
    }
}
