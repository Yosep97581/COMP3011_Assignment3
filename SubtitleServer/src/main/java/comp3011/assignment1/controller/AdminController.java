// AI-assisted: initial controller implementation generated with ChatGPT and reviewed by me. 
// This controller exposes the uptime and graceful shutdown APIs required by the YAML specification.

package comp3011.assignment1.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import comp3011.assignment1.model.ErrorResponse;
import comp3011.assignment1.model.ShutdownResponse;
import comp3011.assignment1.service.ServerLifecycleService;

@RestController
public class AdminController {

    private final ServerLifecycleService lifecycleService;

    public AdminController(ServerLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/api/v1/admin/uptime")
    public ResponseEntity<?> getServerUptime() {
        try {
            return ResponseEntity.ok(lifecycleService.getUptime());

        } catch (Exception e) {
            ErrorResponse error = new ErrorResponse(
                    Instant.now().toString(),
                    500,
                    "Internal Server Error",
                    "An unexpected server error occurred.",
                    "/api/v1/admin/uptime");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }

    @PostMapping("/api/v1/admin/shutdown")
    public ResponseEntity<?> shutdownServer() {

        boolean accepted = lifecycleService.requestShutdown();

        if (!accepted) {
            ErrorResponse error = new ErrorResponse(
                    Instant.now().toString(),
                    409,
                    "Conflict",
                    "Graceful shutdown is already in progress.",
                    "/api/v1/admin/shutdown");

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(error);
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new ShutdownResponse(
                        "Graceful shutdown requested."));
    }
}