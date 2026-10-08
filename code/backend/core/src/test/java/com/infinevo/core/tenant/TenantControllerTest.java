package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantControllerTest {

    private MockMvc mvc;
    private TenantService tenantService;
    private TenantQueryService tenantQueryService;
    private TenantProfileService tenantProfileService;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        tenantQueryService = mock(TenantQueryService.class);
        tenantProfileService = mock(TenantProfileService.class);

        TenantController controller = new TenantController(tenantService, tenantQueryService, tenantProfileService);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/tenants returns 200 with list of tenants")
    void listTenants_returns200WithList() throws Exception {
        TenantOverview overview = new TenantOverview(
                TENANT_ID,
                "Acme Corp",
                "IN",
                "Asia/Kolkata",
                "active",
                List.of("PAYROLL"),
                Instant.parse("2026-01-01T00:00:00Z"),
                LocalDate.of(2026, 12, 31),
                10L,
                new TenantOverview.AdminInvitation("admin@acme.test", TenantOverview.AdminInvitationStatus.PENDING));

        when(tenantQueryService.list()).thenReturn(List.of(overview));

        mvc.perform(get("/api/v1/tenants").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenant_id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Acme Corp"))
                .andExpect(jsonPath("$[0].country_code").value("IN"))
                .andExpect(jsonPath("$[0].timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].modules[0]").value("PAYROLL"))
                .andExpect(jsonPath("$[0].user_count").value(10))
                .andExpect(jsonPath("$[0].admin_invitation.email").value("admin@acme.test"))
                .andExpect(jsonPath("$[0].admin_invitation.status").value("PENDING"));

        verify(tenantQueryService).list();
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} returns 200 with tenant overview")
    void getTenant_returns200WithOverview() throws Exception {
        TenantOverview overview = new TenantOverview(
                TENANT_ID,
                "Acme Corp",
                "IN",
                "Asia/Kolkata",
                "active",
                List.of("PAYROLL"),
                Instant.parse("2026-01-01T00:00:00Z"),
                LocalDate.of(2026, 12, 31),
                10L,
                new TenantOverview.AdminInvitation("admin@acme.test", TenantOverview.AdminInvitationStatus.PENDING));

        when(tenantQueryService.getOverview(TENANT_ID)).thenReturn(overview);

        mvc.perform(get("/api/v1/tenants/{id}", TENANT_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.user_count").value(10));

        verify(tenantQueryService).getOverview(TENANT_ID);
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} returns 404 when tenant is not found")
    void getTenant_whenNotFound_returns404() throws Exception {
        when(tenantQueryService.getOverview(TENANT_ID)).thenThrow(new TenantNotFoundException(TENANT_ID));

        mvc.perform(get("/api/v1/tenants/{id}", TENANT_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Tenant not found: " + TENANT_ID));
    }

    @Test
    @DisplayName("POST /api/v1/tenants returns 201 with created tenant")
    void createTenant_returns201() throws Exception {
        TenantRequest request = new TenantRequest(
                "New Tenant", "IN", "Asia/Kolkata", (short) 4, Set.of(PlatformModule.PAYROLL), "Admin@Acme.test");
        TenantResponse response = new TenantResponse(
                TENANT_ID,
                TENANT_ID,
                "New Tenant",
                "IN",
                "Asia/Kolkata",
                (short) 4,
                Set.of(PlatformModule.PAYROLL),
                INVITATION_ID);

        when(tenantService.provisionTenant(any(TenantRequest.class), any())).thenReturn(response);

        mvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tenants/" + TENANT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("New Tenant"))
                .andExpect(jsonPath("$.adminInvitationId").value(INVITATION_ID.toString()));

        // D-42: the body's admin_email reaches the service under its snake_case name.
        ArgumentCaptor<TenantRequest> sent = ArgumentCaptor.forClass(TenantRequest.class);
        verify(tenantService).provisionTenant(sent.capture(), any());
        assertThat(sent.getValue().adminEmail()).isEqualTo("Admin@Acme.test");
    }

    @Test
    @DisplayName("W-73.2: GET /api/v1/tenants/summary returns the dashboard figures, not a tenant lookup")
    void summary_returns200() throws Exception {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("ACTIVE", 3L);
        byStatus.put("SUSPENDED", 1L);
        TenantSummaryResponse summary = new TenantSummaryResponse(
                4,
                byStatus,
                2,
                List.of(new TenantSummaryResponse.RecentTenant(
                        TENANT_ID, "Acme Corp", Instant.parse("2026-10-01T00:00:00Z"), "ACTIVE")),
                List.of(new TenantSummaryResponse.WaitingTenant(
                        TENANT_ID, "Acme Corp", "admin@acme.test", "EXPIRED", Instant.parse("2026-10-02T00:00:00Z"))));
        when(tenantQueryService.summary()).thenReturn(summary);

        mvc.perform(get("/api/v1/tenants/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(3))
                .andExpect(jsonPath("$.byStatus.SUSPENDED").value(1))
                .andExpect(jsonPath("$.createdLast30Days").value(2))
                .andExpect(jsonPath("$.recent[0].id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.recent[0].name").value("Acme Corp"))
                .andExpect(jsonPath("$.waitingForAdmin[0].adminEmail").value("admin@acme.test"))
                .andExpect(jsonPath("$.waitingForAdmin[0].invitationStatus").value("EXPIRED"));
    }

    @Test
    @DisplayName("W-73.2: POST /{id}/admin-invitation/resend answers 204")
    void resendAdminInvitation_returns204() throws Exception {
        mvc.perform(post("/api/v1/tenants/" + TENANT_ID + "/admin-invitation/resend"))
                .andExpect(status().isNoContent());

        verify(tenantService).resendAdminInvitation(eq(TENANT_ID), any());
    }

    @Test
    @DisplayName("W-73.2: resend with no waiting invitation answers 409 CONFLICT; an unknown tenant 404")
    void resendAdminInvitation_notWaiting409_unknown404() throws Exception {
        doThrow(new AdminInvitationNotWaitingException(TENANT_ID))
                .when(tenantService)
                .resendAdminInvitation(eq(TENANT_ID), any());
        mvc.perform(post("/api/v1/tenants/" + TENANT_ID + "/admin-invitation/resend"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        UUID raced = UUID.randomUUID();
        doThrow(new IllegalStateException("An active pending invitation already exists for a@b.test"))
                .when(tenantService)
                .resendAdminInvitation(eq(raced), any());
        mvc.perform(post("/api/v1/tenants/" + raced + "/admin-invitation/resend"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        UUID unknown = UUID.randomUUID();
        doThrow(new TenantNotFoundException(unknown)).when(tenantService).resendAdminInvitation(eq(unknown), any());
        mvc.perform(post("/api/v1/tenants/" + unknown + "/admin-invitation/resend"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }
}
