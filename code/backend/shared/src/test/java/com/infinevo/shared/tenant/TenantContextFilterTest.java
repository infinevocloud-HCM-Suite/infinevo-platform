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
        objectMapper = new ObjectMapper();
        filter = new TenantContextFilter(extractor, membershipService, objectMapper);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);

        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
}
