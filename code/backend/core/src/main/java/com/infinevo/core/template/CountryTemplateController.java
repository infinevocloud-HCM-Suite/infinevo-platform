package com.infinevo.core.template;

import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which countries have a template and what it holds (W-73.9), for Create Tenant's "Starts with" line.
 * Platform staff only, as provisioning is.
 */
@RestController
@RequestMapping("/api/v1/reference/country-templates")
public class CountryTemplateController {

    private final TenantTemplateService templateService;

    public CountryTemplateController(TenantTemplateService templateService) {
        this.templateService = Objects.requireNonNull(templateService, "templateService must not be null");
    }

    @GetMapping
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<List<CountryTemplateSummary>> list() {
        return ResponseEntity.ok(templateService.countries());
    }
}
