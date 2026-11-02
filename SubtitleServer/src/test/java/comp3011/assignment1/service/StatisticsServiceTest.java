// AI-assisted: initial concurrency test generated with ChatGPT and reviewed/run
// by me to verify that token counters remain correct under concurrent updates.

package comp3011.assignment1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import comp3011.assignment1.model.GlobalStatsResponse;

class StatisticsServiceTest {

    @Test
    void handlesConcurrentTokenUpdates() throws Exception {

        StatisticsService statisticsService = new StatisticsService();

        int updateCount = 250;
        long inputPerUpdate = 10;
        long outputPerUpdate = 5;

        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // Run many updates at the same time to check for lost counter values.
        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < updateCount; i++) {
                futures.add(executor.submit(() -> {
                    startSignal.await();

                    statisticsService.addUsage(
                            inputPerUpdate,
                            outputPerUpdate);

                    return null;
                }));
            }

            // Release all updates at approximately the same time.
            startSignal.countDown();

            for (Future<?> future : futures) {
                future.get();
            }
        }

        GlobalStatsResponse stats = statisticsService.getStats();

        assertEquals(
                updateCount * inputPerUpdate,
                stats.inputTokens());

        assertEquals(
                updateCount * outputPerUpdate,
                stats.outputTokens());
    }
}