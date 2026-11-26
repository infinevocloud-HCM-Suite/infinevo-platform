package com.infinevo.core.ai;

import com.infinevo.core.ai.help.HelpAccess;
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
 * {@code GET /api/v1/ai/link-targets} (W-114.13): the fixed sub-routes the chat drawer may turn into links for this
 * caller - those whose parent is in the caller's own navigation feed. Menu paths themselves the drawer already has.
 * Needs {@code core.ai.use}; {@code 403 AI_DISABLED} when the helper is off. Not counted against the monthly cap.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiLinkTargetController {

    private final AiAccessGate accessGate;
    private final HelpAccess access;
    private final AiSubRoutes subRoutes;

    public AiLinkTargetController(AiAccessGate accessGate, HelpAccess access, AiSubRoutes subRoutes) {
        this.accessGate = Objects.requireNonNull(accessGate, "accessGate must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.subRoutes = Objects.requireNonNull(subRoutes, "subRoutes must not be null");
    }

    /** The response: sub-route paths only, nothing else. */
    public record LinkTargets(List<String> paths) {}

    @GetMapping("/link-targets")
    @RequiresAction("core.ai.use")
    public LinkTargets linkTargets() {
        if (!accessGate.status().enabled()) {
            throw new AiDisabledException();
        }
        return new LinkTargets(subRoutes.allowedFor(access.currentViewer().screens()));
    }

    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(AiDisabledException e) {
        return AiErrorResponses.disabled(e);
    }
}
