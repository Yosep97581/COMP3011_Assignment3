/*
 * COMP3011 Assignment 3 — removable media availability monitoring.
 * Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks that a media file is still accessible without blocking JavaFX.
 * A removable USB drive can disappear while FFmpeg is still playing buffered
 * frames; this lets the player display a useful recovery message instead of
 * continuing indefinitely or mistaking the failure for normal end-of-video.
 */
public final class MediaAvailabilityMonitor implements AutoCloseable {
    private static final long CHECK_INTERVAL_MS = 750;

    private final Path file;
    private final Runnable onUnavailable;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean reported = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public MediaAvailabilityMonitor(Path file, Runnable onUnavailable) {
        this(file, onUnavailable, CHECK_INTERVAL_MS);
    }

    // Package-private interval allows fast, deterministic tests.
    MediaAvailabilityMonitor(Path file, Runnable onUnavailable, long intervalMs) {
        this.file = Objects.requireNonNull(file);
        this.onUnavailable = Objects.requireNonNull(onUnavailable);
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("Polling interval must be positive.");
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "media-availability-check");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::checkNow, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * A file is usable when its path exists, is a regular file, and is readable.
     * Metadata operations can stall during USB removal, so callers should
     * normally perform this check on the monitor worker instead of JavaFX.
     */
    record MediaIdentity(long size, long modifiedMillis) { }

    /** A lightweight fingerprint used to avoid reusing captions for a different file. */
    static MediaIdentity identity(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile() || !Files.isReadable(file)) {
                return null;
            }
            return new MediaIdentity(attributes.size(), attributes.lastModifiedTime().toMillis());
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    static boolean isAvailable(Path file) {
        return identity(file) != null;
    }

    // Kept package-private for tests without waiting for a background timer.
    void checkNow() {
        if (!closed.get() && !reported.get() && !isAvailable(file)
                && reported.compareAndSet(false, true)) {
            try {
                onUnavailable.run();
            } finally {
                close(); // Only notify once for each playback session.
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
