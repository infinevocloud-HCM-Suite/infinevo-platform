package com.infinevo.core.ai.starter;

import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiDisabledException;
import com.infinevo.core.ai.AiErrorResponses;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiErrorResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/ai/starters} (W-114.22): the suggested questions and tasks for an empty chat. Needs
 * {@code core.ai.use}; {@code 403 AI_DISABLED} when the helper is off for the customer (rule 5). Calls no model and is
 * not counted against the monthly cap. The body carries id, label, prompt and kind only - no tool name, no action
 * (rule 6).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStarterController {

    private final AiAccessGate accessGate;
    private final AiStarterService starters;

    public AiStarterController(AiAccessGate accessGate, AiStarterService starters) {
        this.accessGate = Objects.requireNonNull(accessGate, "accessGate must not be null");
        this.starters = Objects.requireNonNull(starters, "starters must not be null");
    }

    /** One chip. */
    public record AiStarterResponse(String id, String label, String prompt, String kind) {

        static AiStarterResponse of(AiStarter s) {
            return new AiStarterResponse(s.id(), s.label(), s.prompt(), s.kind().name());
        }
    }

    @GetMapping("/starters")
    @RequiresAction("core.ai.use")
    public List<AiStarterResponse> starters() {
        if (!accessGate.status().enabled()) {
            throw new AiDisabledException();
        }
        return starters.forCaller().stream().map(AiStarterResponse::of).toList();
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(AiDisabledException e) {
        return AiErrorResponses.disabled(e);
    }
}
