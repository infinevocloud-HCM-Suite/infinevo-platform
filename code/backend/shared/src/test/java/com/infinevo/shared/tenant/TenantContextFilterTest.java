package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextFilterTest {

    private TenantAuthenticationExtractor extractor;
    private TenantMembershipService membershipService;
    private com.infinevo.shared.impersonation.ImpersonationResolver impersonationResolver;
    private ObjectMapper objectMapper;
    private TenantContextFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        extractor = mock(TenantAuthenticationExtractor.class);
        membershipService = mock(TenantMembershipService.class);
        impersonationResolver = mock(com.infinevo.shared.impersonation.ImpersonationResolver.class);
        objectMapper = new ObjectMapper();
        filter = new TenantContextFilter(extractor, membershipService, objectMapper, impersonationResolver);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);

        TenantContext.clear();
        com.infinevo.shared.impersonation.ActingAs.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        com.infinevo.shared.impersonation.ActingAs.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Exempt public endpoints pass through without requiring authentication or tenant binding")
    void exemptPath_passesThrough() throws Exception {
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("A D-22 public endpoint passes through unbound - it binds its tenant from its own signed token")
    void publicEndpoint_passesThroughUnbound() throws Exception {
        request.setRequestURI(com.infinevo.shared.security.PublicEndpoints.DOCUMENT_DOWNLOAD);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Only the exact public path: one segment deeper is filtered like any other request")
    void publicEndpoint_isNotAPrefix() throws Exception {
        request.setRequestURI(com.infinevo.shared.security.PublicEndpoints.DOCUMENT_DOWNLOAD + "/x");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void unauthenticatedRequest_returns401() throws Exception {
        request.setRequestURI("/api/v1/employees");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Valid JWT request binds TenantContext during request and clears it in finally block")
    void validRequest_bindsAndClearsTenantContext() throws Exception {
        request.setRequestURI("/api/v1/employees");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(
                        new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.of(tenantId), true));
        given(membershipService.isUserMemberOfTenant(userId, tenantId)).willReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // TenantContext must be cleared after request execution
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Request for unpermitted tenant returns 403 Forbidden")
    void unpermittedTenant_returns403() throws Exception {
        request.setRequestURI("/api/v1/employees");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(
                        new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.of(tenantId), true));
        given(membershipService.isUserMemberOfTenant(userId, tenantId)).willReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Single-tenant user with missing tenant claim auto-binds single tenant")
    void missingTenantClaim_singleTenantUser_autoBinds() throws Exception {
        request.setRequestURI("/api/v1/employees");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.empty(), false));
        given(membershipService.getUserTenants(userId)).willReturn(List.of(tenantId));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Multi-tenant user with missing tenant claim returns 401 TENANT_NOT_BOUND")
    void missingTenantClaim_multiTenantUser_returns401() throws Exception {
        request.setRequestURI("/api/v1/employees");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.empty(), false));
        given(membershipService.getUserTenants(userId)).willReturn(List.of(tenantId, UUID.randomUUID()));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("TENANT_NOT_BOUND");
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("Valid X-Impersonation binds target tenant and ActingAs context, and cleans up both")
    void validImpersonation_bindsTargetTenantAndActingAs_andCleansUpBoth() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID targetTenantId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        request.setRequestURI("/api/v1/employees");
        request.addHeader("X-Impersonation", sessionId.toString());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.empty(), false));
        given(impersonationResolver.resolve(sessionId, userId))
                .willReturn(
                        Optional.of(new com.infinevo.shared.impersonation.ImpersonationResolver.ResolvedImpersonation(
                                sessionId,
                                targetTenantId,
                                userId,
                                targetUserId,
                                "target@test.local",
                                java.util.Set.of("core.employee.read"))));

        // When filterChain runs, verify contexts inside filterChain
        org.mockito.Mockito.doAnswer(invocation -> {
                    assertThat(TenantContext.require()).isEqualTo(targetTenantId);
                    assertThat(com.infinevo.shared.impersonation.ActingAs.isActing())
                            .isTrue();
                    com.infinevo.shared.impersonation.ActingAs.Impersonation acting =
                            com.infinevo.shared.impersonation.ActingAs.require();
                    assertThat(acting.platformUserId()).isEqualTo(userId);
                    assertThat(acting.targetUserAccountId()).isEqualTo(targetUserId);
                    assertThat(acting.sessionId()).isEqualTo(sessionId);
                    assertThat(acting.targetEmail()).isEqualTo("target@test.local");
                    assertThat(acting.actionCodes()).containsExactly("core.employee.read");
                    assertThat(org.slf4j.MDC.get(com.infinevo.shared.logging.MdcLoggingContext.ACTING_AS_KEY))
                            .isEqualTo(userId.toString());
                    assertThat(org.slf4j.MDC.get(com.infinevo.shared.logging.MdcLoggingContext.TENANT_ID_KEY))
                            .isEqualTo(targetTenantId.toString());
                    return null;
                })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Thread-leak guard: assert BOTH are empty after filter execution
        assertThat(TenantContext.isBound()).isFalse();
        assertThat(com.infinevo.shared.impersonation.ActingAs.isActing()).isFalse();
        assertThat(org.slf4j.MDC.get(com.infinevo.shared.logging.MdcLoggingContext.ACTING_AS_KEY))
                .isNull();
    }

    @Test
    @DisplayName("X-Impersonation and X-Tenant-Id together returns 400 VALIDATION_FAILED")
    void impersonationAndTenantIdTogether_returns400() throws Exception {
        UUID sessionId = UUID.randomUUID();
        request.setRequestURI("/api/v1/employees");
        request.addHeader("X-Impersonation", sessionId.toString());
        request.addHeader("X-Tenant-Id", tenantId.toString());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_FAILED");
        assertThat(TenantContext.isBound()).isFalse();
        assertThat(com.infinevo.shared.impersonation.ActingAs.isActing()).isFalse();
    }

    @Test
    @DisplayName("Invalid or expired impersonation session returns 403 IMPERSONATION_INVALID")
    void invalidImpersonationSession_returns403() throws Exception {
        UUID sessionId = UUID.randomUUID();
        request.setRequestURI("/api/v1/employees");
        request.addHeader("X-Impersonation", sessionId.toString());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.empty(), false));
        given(impersonationResolver.resolve(sessionId, userId)).willReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("IMPERSONATION_INVALID");
        assertThat(TenantContext.isBound()).isFalse();
        assertThat(com.infinevo.shared.impersonation.ActingAs.isActing()).isFalse();
    }

    @Test
    @DisplayName("Malformed X-Impersonation UUID returns 403 IMPERSONATION_INVALID")
    void malformedImpersonationUuid_returns403() throws Exception {
        request.setRequestURI("/api/v1/employees");
        request.addHeader("X-Impersonation", "not-a-valid-uuid");

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("IMPERSONATION_INVALID");
        assertThat(TenantContext.isBound()).isFalse();
        assertThat(com.infinevo.shared.impersonation.ActingAs.isActing()).isFalse();
    }

    @Test
    @DisplayName("Thread-leak guard: when filterChain throws exception, both TenantContext and ActingAs are cleared")
    void threadLeakGuard_whenFilterChainThrows_cleansUpBoth() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID targetTenantId = UUID.randomUUID();
        request.setRequestURI("/api/v1/employees");
        request.addHeader("X-Impersonation", sessionId.toString());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        given(extractor.extract(any(), eq(auth)))
                .willReturn(new TenantAuthenticationExtractor.TenantExtractionResult(userId, Optional.empty(), false));
        given(impersonationResolver.resolve(sessionId, userId))
                .willReturn(
                        Optional.of(new com.infinevo.shared.impersonation.ImpersonationResolver.ResolvedImpersonation(
                                sessionId, targetTenantId, userId, null, null, java.util.Set.of())));

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated unexpected downstream error"))
                .when(filterChain)
                .doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        // Thread-leak guard: assert BOTH are empty after exception
        assertThat(TenantContext.isBound()).isFalse();
        assertThat(com.infinevo.shared.impersonation.ActingAs.isActing()).isFalse();
        assertThat(org.slf4j.MDC.get(com.infinevo.shared.logging.MdcLoggingContext.ACTING_AS_KEY))
                .isNull();
    }
}
