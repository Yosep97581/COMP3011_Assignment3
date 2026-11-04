// AI-assisted: concurrency test drafted with ChatGPT for student review.
package comp3011.assignment1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SubtitleUsageServiceTest {
    @Test
    void countsConcurrentRequestsWithoutLosingSeconds() throws Exception {
        SubtitleUsageService usage = new SubtitleUsageService();
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                jobs.add(pool.submit(() -> {
                    ready.await();
                    usage.addSeconds(0.25);
                    return null;
                }));
            }
            ready.countDown();
            for (Future<?> job : jobs) {
                job.get();
            }
        }
        assertEquals(25.0, usage.getSeconds(), 1e-9);
    }
}
