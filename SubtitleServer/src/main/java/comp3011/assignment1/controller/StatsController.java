// AI-assisted: initial controller implementation generated with ChatGPT and reviewed by me. 
// This endpoint returns the global STT token counters.

package comp3011.assignment1.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import comp3011.assignment1.model.ErrorResponse;
import comp3011.assignment1.model.GlobalStatsResponse;
import comp3011.assignment1.service.StatisticsService;

@RestController
public class StatsController {

    private final StatisticsService statisticsService;

    public StatsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/api/v1/global/stats")
    public ResponseEntity<?> getGlobalStats() {
        try {
            return ResponseEntity.ok(statisticsService.getStats());

        } catch (Exception e) {
            // Return the error structure required by the API specification.
            ErrorResponse error = new ErrorResponse(
                    Instant.now().toString(),
                    500,
                    "Internal Server Error",
                    "An unexpected server error occurred.",
                    "/api/v1/global/stats");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
}