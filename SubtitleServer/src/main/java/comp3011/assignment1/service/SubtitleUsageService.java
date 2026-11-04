// AI-assisted: thread-safe usage counter drafted with ChatGPT for student review.
package comp3011.assignment1.service;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class SubtitleUsageService {

    // Atomic updates prevent concurrent requests from overwriting each other's totals.
    private final AtomicReference<Double> totalSeconds = new AtomicReference<>(0.0);

    public void addSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Usage duration must be non-negative.");
        }
        totalSeconds.updateAndGet(current -> current + seconds);
    }

    public double getSeconds() {
        return totalSeconds.get();
    }
}
