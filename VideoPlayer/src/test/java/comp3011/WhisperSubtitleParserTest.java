/*
 * COMP3011 Assignment 3.
 * Tests by Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class WhisperSubtitleParserTest {
    @Test
    void convertsWordTimestampsToDisplayableCues() throws Exception {
        String json = """
                {"text":"Hello world. Welcome!", "duration":4.0,
                 "segments":[],
                 "words":[
                   {"start":0.1,"end":0.4,"word":"Hello"},
                   {"start":0.5,"end":1.2,"word":"world."},
                   {"start":2.0,"end":2.3,"word":"Welcome!"}
                 ]}
                """;

        SubtitleTrack track = WhisperSubtitleParser.parse(json);
        assertEquals("", track.textAt(0));
        assertEquals("Hello world.", track.textAt(600_000L));
        assertEquals("", track.textAt(1_800_000L));
        assertEquals("Welcome!", track.textAt(2_200_000L));
        assertEquals("Hello world.", track.textAt(600_000L)); // Seek backwards
    }

    @Test
    void wrapsLongSpeechAndKeepsCuesWithinSpecifications() throws Exception {
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < 28; i++) {
            if (i > 0) words.append(',');
            words.append("{\"start\":").append(i * 0.4)
                    .append(",\"end\":").append(i * 0.4 + 0.2)
                    .append(",\"word\":\"example\"}");
        }
        String json = "{\"duration\":12,\"segments\":[],\"words\":[" + words + "]}";
        SubtitleTrack track = WhisperSubtitleParser.parse(json);
        assertFalse(track.textAt(1_000_000L).isBlank());
        assertFalse(track.textAt(8_500_000L).isBlank());
        for (long t = 0; t < 12_000_000; t += 50_000L) {
            String text = track.textAt(t);
            if (!text.isBlank()) {
                String[] lines = text.split("\\n");
                assertTrue(lines.length <= 2);
                for (String line : lines) assertTrue(line.length() <= 42);
            }
        }
    }

    @Test
    void canFallbackToSegmentTimestampsWhenWordsAreMissing() throws Exception {
        String json = """
                {"duration":3.0,"words":[],
                 "segments":[{"start":0.0,"end":2.0,"text":"An example sentence."}]}
                """;
        assertFalse(WhisperSubtitleParser.parse(json).textAt(1_000_000L).isBlank());
    }

    @Test
    void rejectsResponsesWithoutRequiredFields() {
        assertThrows(IOException.class, () -> WhisperSubtitleParser.parse("{}"));
        assertThrows(IOException.class, () -> WhisperSubtitleParser.parse("not JSON"));
    }
}
