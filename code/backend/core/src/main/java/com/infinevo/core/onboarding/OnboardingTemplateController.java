package com.infinevo.core.onboarding;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/templates")
public class OnboardingTemplateController {

    private final OnboardingService onboardingService;

    public OnboardingTemplateController(OnboardingService onboardingService) {
        this.onboardingService = Objects.requireNonNull(onboardingService, "onboardingService must not be null");
    }

    @GetMapping
    @RequiresAction("core.onboarding.manage")
    public List<OnboardingTaskTemplateResponse> listTemplates() {
        UUID tenantId = TenantContext.require();
        return onboardingService.listTemplates(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.onboarding.manage")
    public OnboardingTaskTemplateResponse createTemplate(@RequestBody CreateTemplateRequest request) {
        UUID tenantId = TenantContext.require();
        return onboardingService.createTemplate(tenantId, request, currentActor());
    }

    @PutMapping("/{id}")
    @RequiresAction("core.onboarding.manage")
    public OnboardingTaskTemplateResponse updateTemplate(
            @PathVariable UUID id, @RequestBody UpdateTemplateRequest request) {
        UUID tenantId = TenantContext.require();
        return onboardingService.updateTemplate(tenantId, id, request, currentActor());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAction("core.onboarding.manage")
    public void deleteTemplate(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();
        onboardingService.deleteTemplate(tenantId, id);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
