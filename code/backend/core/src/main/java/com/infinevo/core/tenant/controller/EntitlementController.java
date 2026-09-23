package com.infinevo.core.tenant.controller;

import com.infinevo.core.tenant.dto.TenantEntitlementResponse;
import com.infinevo.core.tenant.service.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class EntitlementController {

    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
    }

    @GetMapping("/entitlements")
    public ResponseEntity<TenantEntitlementResponse> getEntitlements() {
        UUID tenantId = TenantContext.require();
        TenantEntitlementResponse response = entitlementService.getTenantEntitlements(tenantId);
        return ResponseEntity.ok(response);
    }
}
