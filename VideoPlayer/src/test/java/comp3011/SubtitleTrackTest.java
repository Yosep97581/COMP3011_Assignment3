/*
 * COMP3011 Assignment 3.
 * New tests by Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubtitleTrackTest {
    @Test
    void displaysTextOnlyWithinItsTimeRange() {
        SubtitleTrack track = new SubtitleTrack(List.of(
                new SubtitleCue(1_000_000L, 4_000_000L, "First cue"),
                new SubtitleCue(5_000_000L, 8_000_000L, "Second cue")));

        assertEquals("", track.textAt(999_999L));
        assertEquals("First cue", track.textAt(1_000_000L));
        assertEquals("First cue", track.textAt(3_999_999L));
        assertEquals("", track.textAt(4_000_000L));
        assertEquals("Second cue", track.textAt(5_000_000L));
        assertEquals("", track.textAt(8_000_000L));
    }

    @Test
    void timestampsCanMoveBackwardsAfterSeeking() {
        SubtitleTrack track = SubtitleTrack.demo();
        assertEquals("Press S to hide or show the text.", track.textAt(11_000_000L));
        assertEquals("DEMO: Subtitle timing is working.", track.textAt(2_000_000L));
    }

    @Test
    void validatesLineLengthAndNonOverlappingCues() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubtitleCue(0L, 1L, "x".repeat(43)));
        assertThrows(IllegalArgumentException.class,
                () -> new SubtitleCue(0L, 1L, "One\nTwo\nThree"));
        assertThrows(IllegalArgumentException.class,
                () -> new SubtitleTrack(List.of(
                        new SubtitleCue(0L, 3L, "First"),
                        new SubtitleCue(2L, 4L, "Overlaps"))));
        assertTrue(new SubtitleCue(0L, 1L, "Ready").contains(0L));
    }
}
