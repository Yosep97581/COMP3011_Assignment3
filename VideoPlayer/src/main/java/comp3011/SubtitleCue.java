/*
 * COMP3011 Assignment 3.
 * New file by Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.util.Objects;

/** A subtitle and its start/end times in microseconds from the media timeline. */
public record SubtitleCue(long startUs, long endUs, String text) {
    public SubtitleCue {
        if (startUs < 0 || endUs <= startUs) {
            throw new IllegalArgumentException("Subtitle end must be after its start");
        }
        text = Objects.requireNonNull(text, "Subtitle text cannot be null").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Subtitle text cannot be empty");
        }

        // Assignment 3's display limit: at most two lines, 42 characters each.
        String[] lines = text.split("\\R", -1);
        if (lines.length > 2) {
            throw new IllegalArgumentException("A subtitle can have at most two lines");
        }
        for (String line : lines) {
            if (line.codePointCount(0, line.length()) > 42) {
                throw new IllegalArgumentException("Subtitle line exceeds 42 characters");
            }
        }
    }

    public boolean contains(long timestampUs) {
        // End is exclusive so the old subtitle vanishes exactly at its end time.
        return timestampUs >= startUs && timestampUs < endUs;
    }
}
