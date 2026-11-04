// AI-assisted: subtitle controller tests drafted with ChatGPT for student review.
package comp3011.assignment1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClientException;

import comp3011.assignment1.model.SubtitleResponse;
import comp3011.assignment1.service.SubtitleService;
import comp3011.assignment1.service.SubtitleUsageService;

class SubtitleControllerTest {
    private final SubtitleService service = mock(SubtitleService.class);
    private final SubtitleUsageService usage = new SubtitleUsageService();
    private final SubtitleController controller = new SubtitleController(service, usage);

    @Test
    void missingAudioIsRejected() {
        ResponseEntity<?> response = controller.createSubtitles(null);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void successfulTranscriptionReturnsTimingInformation() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav",
                "audio/wav", new byte[] { 1, 2, 3 });
        SubtitleResponse expected = new SubtitleResponse(
                "Hello", 1.5,
                List.of(new SubtitleResponse.Segment(0, 1.5, "Hello")),
                List.of(new SubtitleResponse.Word(0, 1.5, "Hello")));
        when(service.transcribe(audio)).thenReturn(expected);

        ResponseEntity<?> response = controller.createSubtitles(audio);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, assertInstanceOf(SubtitleResponse.class, response.getBody()));
    }

    @Test
    void upstreamErrorReturnsBadGateway() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav",
                "audio/wav", new byte[] { 1 });
        when(service.transcribe(audio)).thenThrow(new RestClientException("secret upstream error"));
        ResponseEntity<?> response = controller.createSubtitles(audio);
        assertEquals(502, response.getStatusCode().value());
    }

    @Test
    void usageEndpointReturnsSeconds() {
        usage.addSeconds(1.25);
        usage.addSeconds(2.5);
        Map<String, Double> result = controller.getUsage();
        assertEquals(3.75, result.get("usage"), 1e-9);
    }
}
