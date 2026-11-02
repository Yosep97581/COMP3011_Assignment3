// AI-assisted: initial lifecycle implementation generated with ChatGPT and refined through TITAN testing. 
// It tracks server uptime and coordinates graceful shutdown.

package comp3011.assignment1.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import comp3011.assignment1.model.UptimeResponse;

@Service
public class ServerLifecycleService {

    private volatile Instant serverStart;

    private final AtomicBoolean shutdownInProgress;
    private final ConfigurableApplicationContext applicationContext;

    public ServerLifecycleService(
            ConfigurableApplicationContext applicationContext) {

        this.shutdownInProgress = new AtomicBoolean(false);
        this.applicationContext = applicationContext;
    }

    // Record the start time only when Spring has completely finished starting.
    @EventListener(ApplicationReadyEvent.class)
    public void recordServerStart() {
        serverStart = Instant.now();
    }

    public UptimeResponse getUptime() {
        Instant now = Instant.now();

        /*
         * If a request reaches this endpoint while Spring is still starting,
         * use the current time without changing the real server start time.
         */
        Instant start = serverStart;

        if (start == null) {
            start = now;
        }

        double uptimeSeconds =
                Duration.between(start, now).toMillis() / 1000.0;

        return new UptimeResponse(
                start.toString(),
                now.toString(),
                uptimeSeconds);
    }

    public boolean requestShutdown() {
        // Only the first shutdown request should be accepted.
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return false;
        }

        Thread shutdownThread = new Thread(() -> {
            try {
                // Give the HTTP response time to return before closing the server.
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            applicationContext.close();
        });

        shutdownThread.setName("graceful-shutdown");
        shutdownThread.start();

        return true;
    }
}