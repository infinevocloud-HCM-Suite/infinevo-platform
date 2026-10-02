package com.infinevo.shared.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.identity.UserProfileSyncService.SyncOutcome;
import com.infinevo.shared.impersonation.ActingAs;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * W-10, W-65.2 — the profile sync runs for the caller's own tenant, and never while a platform staff
 * member is impersonating inside a customer tenant.
 */
class UserProfileSyncFilterTest {

    private static final UUID CUSTOMER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STAFF = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    private UserProfileSyncService syncService;
    private UserProfileSyncFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        syncService = mock(UserProfileSyncService.class);
        when(syncService.sync(any(), any(), any(), any(), any())).thenReturn(SyncOutcome.UNCHANGED);
        filter = new UserProfileSyncFilter(syncService);
        chain = mock(FilterChain.class);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(STAFF.toString())
                .claim("email", "staff@infinevo.local")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        TenantContext.set(CUSTOMER_TENANT);
    }

    @AfterEach
    void tearDown() {
        ActingAs.clear();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A caller in their own tenant has their profile synced")
    void syncsTheCallersOwnProfile() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(syncService).sync(eq(CUSTOMER_TENANT), eq(STAFF), eq("staff@infinevo.local"), any(), any());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("An impersonating staff member is never written into the customer tenant's user_account")
    void skipsTheSyncWhileImpersonating() throws Exception {
        ActingAs.set(STAFF, null, UUID.randomUUID(), null, Set.of("core.user.read"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(syncService, never()).sync(any(), any(), any(), any(), any());
        verify(chain).doFilter(request, response);
    }
}
