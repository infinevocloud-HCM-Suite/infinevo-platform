package com.infinevo.core.ai;

import com.infinevo.shared.error.ApiErrorResponse;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's view of their tenant's AI helper (W-114.1): {@code GET /api/v1/ai/status}.
 *
 * <p>No {@code @RequiresAction}: every signed-in member may see whether their own tenant has the helper and how much
 * of the month's cap is used, as every member sees their own menu. The tenant is the bound one; the path names none.
 * Exempted with that reason in {@code EndpointGuardCoverageTest}.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

    private final AiAccessGate aiAccessGate;

    public AiStatusController(AiAccessGate aiAccessGate) {
        this.aiAccessGate = Objects.requireNonNull(aiAccessGate, "aiAccessGate must not be null");
    }

    @GetMapping("/status")
    public ResponseEntity<AiStatus> status() {
        return ResponseEntity.ok(aiAccessGate.status());
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(AiDisabledException e) {
        return AiErrorResponses.disabled(e);
    }

    @ExceptionHandler(AiCapReachedException.class)
    public ResponseEntity<ApiErrorResponse> handleCapReached(AiCapReachedException e) {
        return AiErrorResponses.capReached(e);
    }
}
