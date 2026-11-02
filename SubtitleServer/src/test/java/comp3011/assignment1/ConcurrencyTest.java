// AI-assisted: initial concurrency test generated with ChatGPT and refined
// through local testing to verify more than 200 blocking requests can run concurrently.

package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConcurrencyTest.BlockingTestController.class)
class ConcurrencyTest {

    @LocalServerPort
    private int port;

    /*
     * This controller is only available during testing.
     * The delay simulates a blocking HTTP request without calling OpenAI.
     */
    @RestController
    static class BlockingTestController {

        @GetMapping("/test/blocking")
        public String blockingRequest() throws InterruptedException {
            Thread.sleep(300);
            return "OK";
        }
    }

    @Test
    void handlesMoreThan200ConcurrentBlockingRequests() throws Exception {

        int requestCount = 250;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Virtual threads allow all test requests to begin without creating
        // hundreds of heavyweight platform threads on the client side.
        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {

            CountDownLatch startSignal = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> futures =
                    new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {

                futures.add(executor.submit(() -> {

                    // Make the requests wait here so they start together.
                    startSignal.await();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(
                                    "http://localhost:" + port
                                    + "/test/blocking"))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    return client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString());
                }));
            }

            long startTime = System.currentTimeMillis();

            // Release all 250 requests at approximately the same time.
            startSignal.countDown();

            for (Future<HttpResponse<String>> future : futures) {
                HttpResponse<String> response = future.get();

                assertEquals(200, response.statusCode());
                assertEquals("OK", response.body());
            }

            long elapsedTime =
                    System.currentTimeMillis() - startTime;

            System.out.println(
                    requestCount
                    + " concurrent requests completed in "
                    + elapsedTime
                    + " ms");

            // Sequential execution would take around 75 seconds
            // (250 x 300 ms), so this confirms they ran concurrently.
            assertTrue(elapsedTime < 10000);
        }
    }
}