/*
 * COMP3011 Assignment 3 — tests for media loss detection.
 * Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MediaAvailabilityMonitorTest {
    @Test
    void acceptsReadableMediaAndRejectsMissingPathsOrFolders() throws Exception {
        Path media = Files.createTempFile("a3-media", ".mp4");
        Path folder = Files.createTempDirectory("a3-media-dir");
        try {
            assertTrue(MediaAvailabilityMonitor.isAvailable(media));
            assertFalse(MediaAvailabilityMonitor.isAvailable(folder));
            Files.delete(media);
            assertFalse(MediaAvailabilityMonitor.isAvailable(media));
        } finally {
            Files.deleteIfExists(media);
            Files.deleteIfExists(folder);
        }
    }

    @Test
    void identityChangesIfRestoredPathPointsToDifferentMedia() throws Exception {
        Path media = Files.createTempFile("a3-identity", ".mp4");
        try {
            Files.write(media, new byte[] { 1, 2, 3 });
            MediaAvailabilityMonitor.MediaIdentity original =
                    MediaAvailabilityMonitor.identity(media);
            assertTrue(original != null);
            Files.write(media, new byte[] { 1, 2, 3, 4, 5 });
            MediaAvailabilityMonitor.MediaIdentity replacement =
                    MediaAvailabilityMonitor.identity(media);
            assertFalse(original.equals(replacement));
        } finally {
            Files.deleteIfExists(media);
        }
    }

    @Test
    void reportsRemovalExactlyOnceAndDoesNotReportAvailableMedia() throws Exception {
        Path media = Files.createTempFile("a3-monitor", ".mp4");
        AtomicInteger callbacks = new AtomicInteger();
        // Long automatic interval: explicitly poll so this test is not timing-sensitive.
        try (MediaAvailabilityMonitor monitor =
                new MediaAvailabilityMonitor(media, callbacks::incrementAndGet, 60_000)) {
            monitor.checkNow();
            assertEquals(0, callbacks.get());
            Files.delete(media);
            monitor.checkNow();
            monitor.checkNow();
            assertEquals(1, callbacks.get());
        } finally {
            Files.deleteIfExists(media);
        }
    }

    @Test
    void closedMonitorDoesNotReportFileRemoval() throws Exception {
        Path media = Files.createTempFile("a3-closed-monitor", ".mp4");
        AtomicInteger callbacks = new AtomicInteger();
        MediaAvailabilityMonitor monitor =
                new MediaAvailabilityMonitor(media, callbacks::incrementAndGet, 60_000);
        try {
            monitor.close();
            Files.delete(media);
            monitor.checkNow();
            assertEquals(0, callbacks.get());
        } finally {
            monitor.close();
            Files.deleteIfExists(media);
        }
    }
}
