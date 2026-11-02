// AI-assisted: initial test implementation generated with ChatGPT and reviewed
// by me to verify successful and repeated graceful shutdown responses.

package comp3011.assignment1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import comp3011.assignment1.model.ErrorResponse;
import comp3011.assignment1.model.ShutdownResponse;
import comp3011.assignment1.service.ServerLifecycleService;

class AdminControllerTest {

    @Test
    void acceptsFirstShutdownRequest() {

        ServerLifecycleService lifecycleService =
                mock(ServerLifecycleService.class);

        when(lifecycleService.requestShutdown()).thenReturn(true);

        AdminController controller =
                new AdminController(lifecycleService);

        ResponseEntity<?> response =
                controller.shutdownServer();

        assertEquals(202, response.getStatusCode().value());

        ShutdownResponse body =
                (ShutdownResponse) response.getBody();

        assertEquals(
                "Graceful shutdown requested.",
                body.message());
    }

    @Test
    void returnsConflictWhenShutdownAlreadyStarted() {

        ServerLifecycleService lifecycleService =
                mock(ServerLifecycleService.class);

        // Simulate a shutdown that has already been requested.
        when(lifecycleService.requestShutdown()).thenReturn(false);

        AdminController controller =
                new AdminController(lifecycleService);

        ResponseEntity<?> response =
                controller.shutdownServer();

        assertEquals(409, response.getStatusCode().value());

        ErrorResponse body =
                (ErrorResponse) response.getBody();

        assertEquals(409, body.status());
        assertEquals("Conflict", body.error());
        assertEquals(
                "Graceful shutdown is already in progress.",
                body.message());
        assertEquals(
                "/api/v1/admin/shutdown",
                body.path());
    }
}