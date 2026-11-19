package com.infinevo.core.onboarding;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
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
