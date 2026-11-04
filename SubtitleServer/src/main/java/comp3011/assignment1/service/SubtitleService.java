// AI-assisted: initial Whisper subtitle integration drafted with ChatGPT.
// The multipart request, timed response, and confidence filtering should be verified in testing.
package comp3011.assignment1.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import comp3011.assignment1.model.SubtitleResponse;
import tools.jackson.databind.JsonNode;

@Service
public class SubtitleService {

    private static final long MAX_AUDIO_BYTES = 25_000_000L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");

    private final RestClient restClient;
    private final String apiKey;
    private final SubtitleUsageService usageService;

    public SubtitleService(
            RestClient.Builder builder,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            SubtitleUsageService usageService) {
        this.restClient = builder.baseUrl("https://api.openai.com").build();
        this.apiKey = apiKey;
        this.usageService = usageService;
    }

    public SubtitleResponse transcribe(MultipartFile audioFile) throws IOException {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("No audio file was received.");
        }
        if (audioFile.getSize() > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("Audio must be 25 MB or smaller.");
        }

        // Preserve an actual supported extension for OpenAI's audio format detection.
        String filename = safeAudioFilename(audioFile.getOriginalFilename());

        // Do not put the key in the repository or send it back to the video player.
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException();
        }

        ByteArrayResource audioResource = new ByteArrayResource(audioFile.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioResource);
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");

        // This REST call runs in the Spring server, not the JavaFX playback thread.
        JsonNode result = restClient.post()
                .uri("/v1/audio/transcriptions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        SubtitleResponse subtitles = parseResponse(result);

        // Count only successful API requests, using usage.seconds when provided.
        JsonNode apiUsage = result.path("usage").path("seconds");
        double seconds = apiUsage.isNumber()
                ? apiUsage.asDouble()
                : subtitles.duration();
        usageService.addSeconds(seconds);
        return subtitles;
    }

    private static String safeAudioFilename(String originalName) {
        if (originalName == null) {
            throw new IllegalArgumentException("Audio filename is required.");
        }
        String cleanName = originalName.replace('\\', '/');
        cleanName = cleanName.substring(cleanName.lastIndexOf('/') + 1);
        if (!cleanName.matches("[A-Za-z0-9._ -]{1,120}")) {
            throw new IllegalArgumentException("Invalid audio filename.");
        }
        int dot = cleanName.lastIndexOf('.');
        String extension = dot < 0 ? "" : cleanName.substring(dot + 1)
                .toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported audio format.");
        }
        return cleanName;
    }

    // Package-private for focused parser tests without calling a paid API.
    static SubtitleResponse parseResponse(JsonNode result) {
        if (result == null || !result.path("duration").isNumber()
                || !result.path("segments").isArray()
                || !result.path("words").isArray()) {
            throw new IllegalStateException("Whisper returned incomplete subtitle data.");
        }

        double duration = result.path("duration").asDouble();
        if (!Double.isFinite(duration) || duration < 0) {
            throw new IllegalStateException("Whisper returned an invalid duration.");
        }

        List<SubtitleResponse.Segment> segments = new ArrayList<>();
        List<double[]> excludedIntervals = new ArrayList<>();
        for (JsonNode segment : result.path("segments")) {
            double start = segment.path("start").asDouble(-1);
            double end = segment.path("end").asDouble(-1);
            if (!validTimeRange(start, end)) {
                throw new IllegalStateException("Whisper returned invalid segment times.");
            }

            // The assignment uses BOTH conditions to identify unwanted speech.
            double noSpeechProb = segment.path("no_speech_prob").asDouble(0);
            double avgLogProb = segment.path("avg_logprob").asDouble(0);
            if (noSpeechProb > 0.6 && avgLogProb < -1.0) {
                excludedIntervals.add(new double[] { start, end });
                continue;
            }

            String text = segment.path("text").asString("").trim();
            if (!text.isBlank()) {
                segments.add(new SubtitleResponse.Segment(start, end, text));
            }
        }

        List<SubtitleResponse.Word> words = new ArrayList<>();
        for (JsonNode word : result.path("words")) {
            double start = word.path("start").asDouble(-1);
            double end = word.path("end").asDouble(-1);
            if (!validTimeRange(start, end)) {
                throw new IllegalStateException("Whisper returned invalid word times.");
            }
            double midpoint = (start + end) / 2.0;
            boolean excluded = excludedIntervals.stream()
                    .anyMatch(range -> midpoint >= range[0] && midpoint <= range[1]);
            String text = word.path("word").asString("").trim();
            if (!excluded && !text.isBlank()) {
                words.add(new SubtitleResponse.Word(start, end, text));
            }
        }

        String text = String.join(" ", segments.stream()
                .map(SubtitleResponse.Segment::text).toList());
        return new SubtitleResponse(text, duration, List.copyOf(segments), List.copyOf(words));
    }

    private static boolean validTimeRange(double start, double end) {
        return Double.isFinite(start) && Double.isFinite(end)
                && start >= 0 && end >= start;
    }

    public static class MissingApiKeyException extends IllegalStateException {
        public MissingApiKeyException() {
            super("OPENAI_API_KEY is not configured.");
        }
    }
}
