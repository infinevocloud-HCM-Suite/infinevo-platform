package com.infinevo.core.tenant.controller;

import com.infinevo.core.tenant.dto.CreateOrganisationRequest;
import com.infinevo.core.tenant.dto.OrganisationResponse;
import com.infinevo.core.tenant.service.TenantService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final TenantService tenantService;

    public OrganisationController(TenantService tenantService) {
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
    }

    @PostMapping
    public ResponseEntity<OrganisationResponse> createOrganisation(
            @RequestBody CreateOrganisationRequest request, Authentication authentication) {
        UUID creatorUserId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try {
                    creatorUserId = UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {
                    creatorUserId = UUID.nameUUIDFromBytes(sub.getBytes());
                }
            }
        }

        OrganisationResponse response = tenantService.createOrganisation(request, creatorUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
