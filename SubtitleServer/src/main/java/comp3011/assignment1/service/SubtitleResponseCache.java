// COMP3011 Assignment 3: AI-assisted server-side subtitle caching.
// Keeps successful transcriptions in memory and shares work between clients
// requesting the same audio simultaneously.
package comp3011.assignment1.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import comp3011.assignment1.model.SubtitleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A small bounded cache. Errors must not become permanent cache entries. */
final class SubtitleResponseCache {
    private static final Logger logger = LoggerFactory.getLogger(SubtitleResponseCache.class);
    @FunctionalInterface
    interface Loader {
        SubtitleResponse load() throws IOException;
    }

    private final int maxEntries;
    private final Map<String, SubtitleResponse> completed;
    private final ConcurrentHashMap<String, CompletableFuture<SubtitleResponse>> inProgress =
            new ConcurrentHashMap<>();

    SubtitleResponseCache(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("Cache size must be positive.");
        }
        this.maxEntries = maxEntries;
        this.completed = new LinkedHashMap<>(16, 0.75f, true);
    }

    SubtitleResponse getOrLoad(String audioHash, Loader loader) throws IOException {
        SubtitleResponse cached = find(audioHash);
        if (cached != null) {
            logger.info("Subtitles: server cache hit; reusing transcription.");
            return cached;
        }

        CompletableFuture<SubtitleResponse> pending = new CompletableFuture<>();
        CompletableFuture<SubtitleResponse> existing = inProgress.putIfAbsent(audioHash, pending);
        if (existing != null) {
            logger.info("Subtitles: shared a transcription already in progress.");
            try {
                // Another client is already transcribing these exact audio bytes.
                return existing.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for subtitles.", e);
            } catch (ExecutionException e) {
                Throwable failure = e.getCause();
                if (failure instanceof IOException io) {
                    throw io;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IOException("Subtitle transcription failed.", failure);
            }
        }

        try {
            // Check again: a previous request may have finished between find()
            // and obtaining our place in the inProgress map.
            cached = find(audioHash);
            if (cached == null) {
                cached = loader.load();
                if (cached == null) {
                    throw new IllegalStateException("Transcription returned no data.");
                }
                save(audioHash, cached);
            }
            pending.complete(cached);
            return cached;
        } catch (IOException | RuntimeException e) {
            pending.completeExceptionally(e);
            throw e;
        } finally {
            inProgress.remove(audioHash, pending);
        }
    }

    private synchronized SubtitleResponse find(String key) {
        return completed.get(key);
    }

    private synchronized void save(String key, SubtitleResponse value) {
        completed.put(key, value);
        if (completed.size() > maxEntries) {
            String eldest = completed.keySet().iterator().next();
            completed.remove(eldest);
        }
    }
}
