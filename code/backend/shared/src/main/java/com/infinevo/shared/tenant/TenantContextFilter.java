package com.infinevo.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.security.PublicEndpoints;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts tenant claims, verifies membership against {@code core.user_tenant},
 * binds {@link TenantContext} for the request duration, and guarantees {@link TenantContext#clear()} in a {@code finally} block.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * Paths that need no tenant. Health probes and the API description, and nothing else.
     *
     * <p>{@code /api/v1/auth/login} was here until W-10 and is gone on purpose: the platform has no
     * local login path. Keycloak issues every token ({@code ResourceServerConfig}), HRMS's own JWT
     * system is being deleted rather than adapted, and an exemption sitting here is how such a path
     * grows back. Nothing serves it now, so it answers 404 — spec section 8.
     */
    private static final List<String> EXEMPT_PATH_PATTERNS = List.of(
            "/actuator/health",
            // The sub-paths matter as much as the parent. `management.endpoint.health.probes.enabled`
            // is on, so the orchestrator probes /actuator/health/liveness and /readiness, not the
            // parent. ResourceServerConfig permits those, but this filter exempted only the bare
            // path, so a probe passed security, reached here with no authentication and was refused
            // 401 UNAUTHENTICATED - a replica that is alive being restarted for failing its probe.
            // The two lists have to agree; ResourceServerConfig.HEALTH_SUBPATHS is the other half.
            "/actuator/health/**",
            // Error dispatch carries no authentication. Without this a genuine 404 or 500 is
            // re-answered as 401 on the way out, so every server-side fault reads as an auth
            // failure and the real status never reaches the caller.
            "/error",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final TenantAuthenticationExtractor extractor;
    private final TenantMembershipService membershipService;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(
            TenantAuthenticationExtractor extractor,
            TenantMembershipService membershipService,
            ObjectMapper objectMapper) {
        this.extractor = extractor;
        this.membershipService = membershipService;
        this.objectMapper = objectMapper != null
                ? objectMapper.copy().registerModule(new JavaTimeModule())
                : new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Health, error dispatch and the API description by pattern; the application endpoints of
     * {@link PublicEndpoints} by exact path. Those bind their tenant from their own signed token, so a
     * tenant bound here would be the wrong one or none — and the request carries no identity to bind
     * from in any case.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PublicEndpoints.PATHS.contains(path)
                || EXEMPT_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, ApiError.UNAUTHENTICATED, "Authentication required");
            return;
        }

        TenantAuthenticationExtractor.TenantExtractionResult extractionResult;
        try {
            extractionResult = extractor.extract(request, auth);
        } catch (IllegalArgumentException e) {
            writeErrorResponse(response, HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
            return;
        }

        UUID userId = extractionResult.userId();
        Optional<UUID> requestedTenantId = extractionResult.tenantId();

        UUID tenantToBind;
        try {
            if (requestedTenantId.isPresent()) {
                UUID targetTenantId = requestedTenantId.get();
                if (!membershipService.isUserMemberOfTenant(userId, targetTenantId)) {
                    writeErrorResponse(
                            response,
                            HttpStatus.FORBIDDEN,
                            ApiError.FORBIDDEN,
                            "User is not a member of the requested tenant");
                    return;
                }
                tenantToBind = targetTenantId;
            } else {
                List<UUID> userTenants = membershipService.getUserTenants(userId);
                if (userTenants.size() == 1) {
                    tenantToBind = userTenants.get(0);
                } else {
                    writeErrorResponse(
                            response,
                            HttpStatus.UNAUTHORIZED,
                            ApiError.TENANT_NOT_BOUND,
                            "No tenant bound to request. Specify X-Tenant-Id or tenant_id claim");
                    return;
                }
            }
        } catch (TenantMembershipService.TenantMembershipAccessException e) {
            writeErrorResponse(
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiError.INTERNAL,
                    "Database infrastructure error during tenant verification");
            return;
        }

        TenantContext.set(tenantToBind);
        MDC.put(MdcLoggingContext.TENANT_ID_KEY, tenantToBind.toString());
        MDC.put(MdcLoggingContext.USER_ID_KEY, userId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcLoggingContext.USER_ID_KEY);
            MDC.remove(MdcLoggingContext.TENANT_ID_KEY);
            TenantContext.clear();
        }
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, ApiError error, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        ApiErrorResponse errorResponseBody = ApiErrorResponse.of(error, message, traceId);
        objectMapper.writeValue(response.getOutputStream(), errorResponseBody);
    }
}
