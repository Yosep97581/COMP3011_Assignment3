// AI-assisted: subtitle API controller drafted with ChatGPT for student review.
// The original Assignment 1 endpoints remain available and unchanged.
package comp3011.assignment1.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import comp3011.assignment1.model.SubtitleResponse;
import comp3011.assignment1.service.SubtitleService;
import comp3011.assignment1.service.SubtitleUsageService;

@RestController
public class SubtitleController {

    private static final Logger logger = LoggerFactory.getLogger(SubtitleController.class);
    private final SubtitleService subtitleService;
    private final SubtitleUsageService usageService;

    public SubtitleController(SubtitleService subtitleService,
            SubtitleUsageService usageService) {
        this.subtitleService = subtitleService;
        this.usageService = usageService;
    }

    @PostMapping("/api/v1/subtitles")
    public ResponseEntity<?> createSubtitles(
            @RequestParam(value = "audio", required = false) MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "No audio file was received."));
        }
        try {
            SubtitleResponse subtitles = subtitleService.transcribe(audioFile);
            return ResponseEntity.ok(subtitles);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SubtitleService.MissingApiKeyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", "Subtitle transcription is not configured."));
        } catch (RestClientException | IllegalStateException e) {
            // Do not expose OpenAI errors or authentication details to clients.
            logger.warn("Subtitle transcription service unavailable or invalid response.");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", "Subtitle transcription service failed."));
        } catch (IOException e) {
            logger.warn("Unable to read the uploaded audio.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "Could not read the uploaded audio."));
        }
    }

    @GetMapping("/api/v1/global/usage")
    public Map<String, Double> getUsage() {
        return Map.of("usage", usageService.getSeconds());
    }
}
