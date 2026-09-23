package com.infinevo.shared.entitlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.DelegatingServletOutputStream;

class EntitlementEnforcementFilterTest {

    private EntitlementChecker entitlementChecker;
    private EntitlementEnforcementFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        entitlementChecker = Mockito.mock(EntitlementChecker.class);
        filter = new EntitlementEnforcementFilter(entitlementChecker, null);
        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        filterChain = Mockito.mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldBypassExemptPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(entitlementChecker, never()).isTenantEntitled(any(), any());
    }

    @Test
    void shouldAllowAccessWhenTenantIsEntitled() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        when(request.getRequestURI()).thenReturn("/api/v1/hrms/sample");
        when(entitlementChecker.isTenantEntitled(tenantId, ModuleCode.HRMS)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectAccessWith403WhenTenantIsNotEntitled() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        when(request.getRequestURI()).thenReturn("/api/v1/hrms/sample");
        when(entitlementChecker.isTenantEntitled(tenantId, ModuleCode.HRMS)).thenReturn(false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(out));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }
}
