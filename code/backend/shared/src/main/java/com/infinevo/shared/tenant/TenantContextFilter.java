package com.infinevo.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private static final List<String> EXEMPT_PATH_PATTERNS =
            List.of("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/v1/auth/login");

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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXEMPT_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
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

        TenantContext.set(tenantToBind);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
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
