// AI-assisted: initial controller approach generated with ChatGPT and reviewed/adapted by me. 
// It validates uploaded audio, delegates transcription to the service, and handles STT failures.

package comp3011.assignment1.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import comp3011.assignment1.service.TranscriptionResult;
import comp3011.assignment1.service.TranscriptionService;

@RestController
public class TranscriptionController {

    private final TranscriptionService transcriptionService;
    private static final Logger logger =
            LoggerFactory.getLogger(TranscriptionController.class);

    public TranscriptionController(
            TranscriptionService transcriptionService) {

        this.transcriptionService = transcriptionService;
    }

    @PostMapping("/api/v1/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audioFile) {

        if (audioFile.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "No audio file was received.");

            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Pass the audio to the service that communicates with OpenAI.
            TranscriptionResult result =
                    transcriptionService.transcribe(audioFile);

            Map<String, Object> response = new HashMap<>();
            response.put("text", result.text());

            return ResponseEntity.ok(response);

        } catch (RestClientException e) {
            // A 502 indicates that our server could not get a valid response
            // from the external speech-to-text service.
        	logger.warn("Speech-to-text service request failed.");
        	
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(
                    "message",
                    "Speech-to-text service request failed.");

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(errorResponse);

        } catch (IOException | IllegalStateException e) {
        	// Log a generic error without exposing sensitive request or API details.
        	logger.error("Unable to process the uploaded recording.");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(
                    "message",
                    "Unable to process the recording.");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
}