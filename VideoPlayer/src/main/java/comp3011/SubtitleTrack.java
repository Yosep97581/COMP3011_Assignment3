/*
 * COMP3011 Assignment 3.
 * New file by Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.util.List;

/**
 * Timed subtitles for one media file. This milestone uses sample cues, but the
 * cloud response can later be converted into the same SubtitleCue objects.
 */
public class SubtitleTrack {
    private final List<SubtitleCue> cues;

    public SubtitleTrack(List<SubtitleCue> cues) {
        this.cues = List.copyOf(cues);
        for (int i = 1; i < this.cues.size(); i++) {
            if (this.cues.get(i).startUs() < this.cues.get(i - 1).endUs()) {
                throw new IllegalArgumentException("Subtitle cues must be in order and not overlap");
            }
        }
    }

    public String textAt(long timestampUs) {
        // Binary search avoids scanning the whole transcript for every video frame.
        int left = 0;
        int right = cues.size() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            SubtitleCue cue = cues.get(mid);
            if (timestampUs < cue.startUs()) {
                right = mid - 1;
            } else if (timestampUs >= cue.endUs()) {
                left = mid + 1;
            } else {
                return cue.text();
            }
        }
        return "";
    }

    /** Temporary captions to test seeking, pausing and the CC toggle without a server. */
    public static SubtitleTrack demo() {
        return new SubtitleTrack(List.of(
                new SubtitleCue(1_000_000L, 4_000_000L,
                        "DEMO: Subtitle timing is working."),
                new SubtitleCue(5_000_000L, 8_000_000L,
                        "These are sample subtitles,\nnot actual speech recognition."),
                new SubtitleCue(10_000_000L, 13_000_000L,
                        "Press S to hide or show the text."),
                new SubtitleCue(15_000_000L, 19_000_000L,
                        "Try pausing or seeking five seconds."),
                new SubtitleCue(21_000_000L, 24_000_000L,
                        "Cloud transcription comes next.")));
    }
}
