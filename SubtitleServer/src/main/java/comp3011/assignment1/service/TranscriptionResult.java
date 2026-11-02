// AI-assisted: simple result record generated with ChatGPT and reviewed 
// by me to pass transcription text and token usage between services.

package comp3011.assignment1.service;

public record TranscriptionResult(
        String text,
        long inputTokens,
        long outputTokens) {
}
