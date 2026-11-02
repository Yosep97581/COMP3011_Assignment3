// AI-assisted: initial test implementation generated with ChatGPT and reviewed
// by me to test successful transcription, empty audio, and STT service failures.

package comp3011.assignment1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClientException;

import comp3011.assignment1.service.TranscriptionResult;
import comp3011.assignment1.service.TranscriptionService;

class TranscriptionControllerTest {

    @Test
    void rejectsEmptyAudioFile() throws Exception {

        TranscriptionService service = mock(TranscriptionService.class);
        TranscriptionController controller =
                new TranscriptionController(service);

        MockMultipartFile emptyAudio = new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                new byte[0]);

        ResponseEntity<Map<String, Object>> response =
                controller.transcribe(emptyAudio);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "No audio file was received.",
                response.getBody().get("message"));
    }

    @Test
    void returnsTranscriptionWhenServiceSucceeds() throws Exception {

        TranscriptionService service = mock(TranscriptionService.class);
        TranscriptionController controller =
                new TranscriptionController(service);

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                "fake audio data".getBytes());

        // Use a fake STT result so the test does not call the real OpenAI API.
        when(service.transcribe(audio))
                .thenReturn(new TranscriptionResult(
                        "Hello from the test.",
                        20,
                        5));

        ResponseEntity<Map<String, Object>> response =
                controller.transcribe(audio);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(
                "Hello from the test.",
                response.getBody().get("text"));
    }

    @Test
    void returnsBadGatewayWhenSpeechServiceFails() throws Exception {

        TranscriptionService service = mock(TranscriptionService.class);
        TranscriptionController controller =
                new TranscriptionController(service);

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                "fake audio data".getBytes());

        // Simulate the external speech-to-text service being unavailable.
        when(service.transcribe(audio))
                .thenThrow(new RestClientException("STT unavailable"));

        ResponseEntity<Map<String, Object>> response =
                controller.transcribe(audio);

        assertEquals(502, response.getStatusCode().value());
        assertEquals(
                "Speech-to-text service request failed.",
                response.getBody().get("message"));
    }
}