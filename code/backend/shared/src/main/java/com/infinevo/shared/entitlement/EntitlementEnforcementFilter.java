package com.infinevo.shared.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that inspects target API endpoints, resolves required module entitlement,
 * and rejects un-entitled tenant requests with HTTP 403 Forbidden.
 */
public class EntitlementEnforcementFilter extends OncePerRequestFilter {

    private static final List<String> EXEMPT_PATH_PATTERNS = List.of(
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/auth/login",
            "/api/v1/organisations",
            "/api/v1/tenant/entitlements");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final EntitlementChecker entitlementChecker;
    private final ObjectMapper objectMapper;

    public EntitlementEnforcementFilter(EntitlementChecker entitlementChecker, ObjectMapper objectMapper) {
        this.entitlementChecker = entitlementChecker;
        this.objectMapper = objectMapper != null
                ? objectMapper.copy().registerModule(new JavaTimeModule())
                : new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXEMPT_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        UUID tenantId = TenantContext.current().orElse(null);
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        ModuleCode requiredModule = resolveRequiredModule(path);

        if (requiredModule != null && requiredModule != ModuleCode.CORE) {
            boolean isEntitled =
                    entitlementChecker != null && entitlementChecker.isTenantEntitled(tenantId, requiredModule);
            if (!isEntitled) {
                writeErrorResponse(
                        response,
                        HttpStatus.FORBIDDEN,
                        ApiError.FORBIDDEN,
                        "Tenant is not entitled to access module " + requiredModule.name());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private ModuleCode resolveRequiredModule(String path) {
        if (path.startsWith("/api/v1/hrms")) {
            return ModuleCode.HRMS;
        } else if (path.startsWith("/api/v1/payroll")) {
            return ModuleCode.PAYROLL;
        }
        return null;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, ApiError error, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ApiErrorResponse errorResponseBody = ApiErrorResponse.of(error, message, traceId);
        objectMapper.writeValue(response.getOutputStream(), errorResponseBody);
    }
}
