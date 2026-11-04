// AI-assisted: subtitle response structure drafted with ChatGPT for student review.
// The timestamps are in seconds from the beginning of the supplied audio.
package comp3011.assignment1.model;

import java.util.List;

public record SubtitleResponse(
        String text,
        double duration,
        List<Segment> segments,
        List<Word> words) {

    public record Segment(double start, double end, String text) { }

    public record Word(double start, double end, String word) { }
}
