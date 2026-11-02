// AI-assisted: initial implementation generated with ChatGPT and reviewed by me. 
// Atomic counters are used so token updates remain safe under concurrent requests.

package comp3011.assignment1.service;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import comp3011.assignment1.model.GlobalStatsResponse;

@Service
public class StatisticsService {

    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);

    // Add token usage after each successful transcription.
    public void addUsage(long input, long output) {
        inputTokens.addAndGet(input);
        outputTokens.addAndGet(output);
    }

    public GlobalStatsResponse getStats() {
        return new GlobalStatsResponse(
                inputTokens.get(),
                outputTokens.get());
    }
}