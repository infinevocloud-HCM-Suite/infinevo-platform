package com.infinevo.core.portal;

import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service portal controller (W-25, spec section 4).
 *
 * <p>Serves {@code GET /api/v1/me/panels}, returning the ordered list of panel descriptors the caller
 * may see. Gated by {@code core.employee.read_own} — the least any portal user holds.
 */
@RestController
@RequestMapping("/api/v1/me/panels")
public class PortalController {

    private final PortalPanelService portalPanelService;

    public PortalController(PortalPanelService portalPanelService) {
        this.portalPanelService = Objects.requireNonNull(portalPanelService, "portalPanelService must not be null");
    }

    @GetMapping
    @RequiresAction("core.employee.read_own")
    public ResponseEntity<List<PanelDescriptor>> getPanels() {
        return ResponseEntity.ok(portalPanelService.getPanels());
    }
}
