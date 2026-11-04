// AI-assisted: Whisper response parsing tests drafted with ChatGPT for student review.
package comp3011.assignment1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SubtitleServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void ignoresSegmentsMeetingBothLowConfidenceConditions() {
        String json = """
                {
                  "duration": 4.5,
                  "segments": [
                    {"start": 0, "end": 2, "text": " Hello there", "no_speech_prob": 0.1, "avg_logprob": -0.2},
                    {"start": 2, "end": 4.5, "text": " Bad noise", "no_speech_prob": 0.8, "avg_logprob": -1.3}
                  ],
                  "words": [
                    {"word": "Hello", "start": 0, "end": 1},
                    {"word": "there", "start": 1, "end": 2},
                    {"word": "Bad", "start": 2.2, "end": 2.8},
                    {"word": "noise", "start": 3, "end": 4}
                  ]
                }
                """;
        var response = SubtitleService.parseResponse(mapper.readTree(json));
        assertEquals("Hello there", response.text());
        assertEquals(4.5, response.duration(), 1e-9);
        assertEquals(1, response.segments().size());
        assertEquals(2, response.words().size());
    }

    @Test
    void requiresBothConditionsToExcludeSegment() {
        String json = """
                {"duration": 1, "segments": [
                  {"start": 0, "end": 1, "text": " Keep", "no_speech_prob": 0.9, "avg_logprob": -0.4}
                ], "words": []}
                """;
        var response = SubtitleService.parseResponse(mapper.readTree(json));
        assertEquals("Keep", response.text());
    }

    @Test
    void rejectsMissingTimestampArrays() {
        String json = "{\"duration\": 1, \"text\": \"hi\"}";
        assertThrows(IllegalStateException.class,
                () -> SubtitleService.parseResponse(mapper.readTree(json)));
    }
}
