// AI-assisted: initial regression test implementation generated with ChatGPT 
// and reviewed/run by me to verify the required API fields.

package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiRegressionTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void uptimeEndpointReturnsExpectedFields() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port
                        + "/api/v1/admin/uptime"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        // Check that the response still follows the required API structure.
        assertTrue(response.body().contains("utcServerStart"));
        assertTrue(response.body().contains("utcNow"));
        assertTrue(response.body().contains("serverUptimeSeconds"));
    }

    @Test
    void globalStatsEndpointReturnsExpectedFields() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port
                        + "/api/v1/global/stats"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        // Both token counters must always be included in the response.
        assertTrue(response.body().contains("inputTokens"));
        assertTrue(response.body().contains("outputTokens"));
    }
}