// AI-assisted: initial OpenAI integration generated with ChatGPT and reviewed/adapted by me. 
// The service keeps the API key server-side, sends multipart audio to the STT API, and records token usage.

package comp3011.assignment1.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;

@Service
public class TranscriptionService {

    private final RestClient restClient;
    private final String apiKey;
    private final StatisticsService statisticsService;

    public TranscriptionService(
            RestClient.Builder restClientBuilder,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            StatisticsService statisticsService) {

        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com")
                .build();

        this.apiKey = apiKey;
        this.statisticsService = statisticsService;
    }

    public TranscriptionResult transcribe(MultipartFile audioFile)
            throws IOException {

        // The API key must come from the environment and must not be stored in code.
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY environment variable is not configured.");
        }

        // Convert the uploaded recording into a resource for the multipart request.
        ByteArrayResource audioResource =
                new ByteArrayResource(audioFile.getBytes()) {
                    @Override
                    public String getFilename() {
                        return "recording.webm";
                    }
                };

        MultiValueMap<String, Object> requestBody =
                new LinkedMultiValueMap<>();

        requestBody.add("file", audioResource);
        requestBody.add("model", "gpt-4o-mini-transcribe");

        // Send the recording to the external speech-to-text service.
        JsonNode response = restClient.post()
                .uri("/v1/audio/transcriptions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.get("text") == null) {
            throw new IllegalStateException(
                    "The speech-to-text service returned an invalid response.");
        }

        String text = response.get("text").asString();

        // Save the token values now because they are required for global statistics later.
        long inputTokens = response.path("usage")
                .path("input_tokens")
                .asLong(0);

        long outputTokens = response.path("usage")
                .path("output_tokens")
                .asLong(0);
        
        // Update the global counters after a successful transcription.
        statisticsService.addUsage(inputTokens, outputTokens);
        
        return new TranscriptionResult(
                text,
                inputTokens,
                outputTokens);
    }
}