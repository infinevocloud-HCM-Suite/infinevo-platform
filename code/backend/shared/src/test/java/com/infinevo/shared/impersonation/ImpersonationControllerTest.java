package com.infinevo.shared.impersonation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ImpersonationControllerTest {

    private MockMvc mvc;
    private ImpersonationService impersonationService;
    private PlatformTenant platformTenant;
    private ObjectMapper objectMapper;

    private final UUID customerTenantId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        impersonationService = mock(ImpersonationService.class);
        platformTenant = new PlatformTenant();
        ImpersonationController controller = new ImpersonationController(impersonationService, platformTenant);

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        TenantContext.set(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);
        Jwt staffJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(staffUserId.toString())
                .claim("email", "staff@infinevo.local")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(staffJwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/tenants/{id}/impersonations opens session and returns 201 CREATED")
    void openImpersonation_success_returns201() throws Exception {
        Instant expiresAt = Instant.parse("2026-09-29T22:00:00Z");
        ImpersonationService.OpenedImpersonation opened = new ImpersonationService.OpenedImpersonation(
                sessionId, customerTenantId, targetUserId, "user@customer.local", expiresAt);

        given(impersonationService.openImpersonation(
                        eq(customerTenantId),
                        eq(staffUserId),
                        eq(targetUserId),
                        eq("user@customer.local"),
                        eq("Investigating issue")))
                .willReturn(opened);

        String requestBody =
                """
                {
                    "userAccountId": "%s",
                    "email": "user@customer.local",
                    "reason": "Investigating issue"
                }
                """
                        .formatted(targetUserId);

        mvc.perform(post("/api/v1/tenants/{id}/impersonations", customerTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.tenantId").value(customerTenantId.toString()))
                .andExpect(jsonPath("$.userAccountId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.userEmail").value("user@customer.local"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-29T22:00:00Z"));

        verify(impersonationService)
                .openImpersonation(
                        customerTenantId, staffUserId, targetUserId, "user@customer.local", "Investigating issue");
    }

    @Test
    @DisplayName("POST /api/v1/tenants/{id}/impersonations returns 404 USER_NOT_FOUND when user does not exist")
    void openImpersonation_unknownUser_returns404() throws Exception {
        given(impersonationService.openImpersonation(any(), any(), any(), any(), any()))
                .willThrow(new ImpersonationService.UserNotFoundException("Target user not found"));

        String requestBody =
                """
                {
                    "email": "nonexistent@customer.local",
                    "reason": "Investigating issue"
                }
                """;

        mvc.perform(post("/api/v1/tenants/{id}/impersonations", customerTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Target user not found"));
    }

    @Test
    @DisplayName("POST /api/v1/tenants/{id}/impersonations returns 404 TENANT_NOT_FOUND when tenant does not exist")
    void openImpersonation_unknownTenant_returns404() throws Exception {
        given(impersonationService.openImpersonation(any(), any(), any(), any(), any()))
                .willThrow(new ImpersonationService.TenantNotFoundException("Tenant not found"));

        String requestBody =
                """
                {
                    "reason": "Bootstrap session"
                }
                """;

        mvc.perform(post("/api/v1/tenants/{id}/impersonations", customerTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Tenant not found"));
    }

    @Test
    @DisplayName("POST /api/v1/tenants/{id}/impersonations returns 400 VALIDATION_FAILED when reason is invalid")
    void openImpersonation_blankReason_returns400() throws Exception {
        given(impersonationService.openImpersonation(any(), any(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("Reason must not be blank"));

        String requestBody =
                """
                {
                    "reason": "   "
                }
                """;

        mvc.perform(post("/api/v1/tenants/{id}/impersonations", customerTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Reason must not be blank"));
    }

    @Test
    @DisplayName("DELETE /api/v1/impersonations/{id} closes session and returns 204 NO_CONTENT")
    void closeImpersonation_success_returns204() throws Exception {
        mvc.perform(delete("/api/v1/impersonations/{id}", sessionId)).andExpect(status().isNoContent());

        verify(impersonationService).closeImpersonation(sessionId, staffUserId);
    }

    @Test
    @DisplayName("DELETE /api/v1/impersonations/{id} returns 404 when session not found or unowned")
    void closeImpersonation_notFound_returns404() throws Exception {
        willThrow(new ImpersonationService.SessionNotFoundException("Session not found"))
                .given(impersonationService)
                .closeImpersonation(sessionId, staffUserId);

        mvc.perform(delete("/api/v1/impersonations/{id}", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Calling endpoints outside platform tenant returns 403 FORBIDDEN")
    void nonPlatformTenant_returns403() throws Exception {
        TenantContext.set(customerTenantId); // not platform tenant!

        mvc.perform(post("/api/v1/tenants/{id}/impersonations", customerTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(delete("/api/v1/impersonations/{id}", sessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
