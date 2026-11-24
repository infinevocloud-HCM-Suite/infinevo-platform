package com.infinevo.payroll.statutory.guided;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.statutory.pt.OrgPtOverride;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideSlabRepository;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Acceptance integration tests for Guided Statutory Setup (W-93, spec section 10).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class GuidedStatutorySetupIT extends AbstractIntegrationTest {

    @Autowired
    private GuidedStatutorySetupService guidedSetupService;

    @Autowired
    private ProfessionalTaxService professionalTaxService;

    @Autowired
    private EpfSettingRepository epfSettingRepository;

    @Autowired
    private EsiSettingRepository esiSettingRepository;

    @Autowired
    private OrgPtOverrideRepository orgPtOverrideRepository;

    @Autowired
    private OrgPtOverrideSlabRepository orgPtOverrideSlabRepository;

    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        TenantContext.set(TENANT_A);

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Seed an active work location in Karnataka
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (tenant_id, code, name, state, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "BLR");
            ps.setString(3, "Bangalore Office");
            ps.setString(4, "Karnataka");
            ps.setString(5, "KA");
            ps.setBoolean(6, true);
            ps.executeUpdate();
        }

        when(permissionService.holds(anyString())).thenReturn(true);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        GuidedStatutorySetupController controller = new GuidedStatutorySetupController(guidedSetupService);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));

        mvc = MockMvcBuilders.standaloneSetup((GuidedStatutorySetupController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        when(permissionService.holds(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/guided-setup returns 403 when lacking payroll.salary.read")
    void getReadinessForbiddenWithoutPermission() throws Exception {
        when(permissionService.holds("payroll.salary.read")).thenReturn(false);

        mvc.perform(get("/api/v1/payroll/settings/guided-setup")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/guided-setup returns 200 with readiness and statutory defaults")
    void getReadinessSuccess() throws Exception {
        mvc.perform(get("/api/v1/payroll/settings/guided-setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.epf_configured").value(false))
                .andExpect(jsonPath("$.data.esi_configured").value(false))
                .andExpect(jsonPath("$.data.defaults.epf_employee_rate").value(12.0))
                .andExpect(jsonPath("$.data.defaults.epf_wage_ceiling").value(15000.0))
                .andExpect(jsonPath("$.data.defaults.esi_wage_ceiling").value(21000.0))
                .andExpect(jsonPath("$.data.operating_states[0].state_code").value("KA"))
                .andExpect(jsonPath("$.data.operating_states[0].levies_pt").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/payroll/settings/guided-setup returns 403 when lacking payroll.settings.manage")
    void applySetupForbiddenWithoutPermission() throws Exception {
        when(permissionService.holds("payroll.settings.manage")).thenReturn(false);

        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "DLCPM1234567000", LocalDate.of(2026, 4, 1), true, true, false, false),
                new EsiSetupRequest(false, null, null, false),
                List.of());

        mvc.perform(post("/api/v1/payroll/settings/guided-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/payroll/settings/guided-setup persists EPF, ESI, and PT override without copying slabs")
    void applySetupPersistsRecordsAndDoesNotCopySlabs() throws Exception {
        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "DLCPM1234567000", LocalDate.of(2026, 4, 1), true, true, false, false),
                new EsiSetupRequest(true, "31000123450000101", LocalDate.of(2026, 4, 1), true),
                List.of(new PtStateSetupRequest("KA", "29123456789", LocalDate.of(2025, 4, 1))));

        mvc.perform(post("/api/v1/payroll/settings/guided-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.all_statutory_configured").value(true))
                .andExpect(jsonPath("$.data.epf.is_enabled").value(true))
                .andExpect(jsonPath("$.data.esi.is_enabled").value(true));

        // Verify persisted entities in database
        Optional<EpfSetting> epf = epfSettingRepository.findByTenantId(TENANT_A);
        assertThat(epf).isPresent();
        assertThat(epf.get().isEnabled()).isTrue();
        assertThat(epf.get().getRegistrationNumber()).isEqualTo("DLCPM1234567000");
        assertThat(epf.get().getEmployeeRate()).isEqualByComparingTo("12.0000");
        assertThat(epf.get().getWageCeiling()).isEqualByComparingTo("15000.0000");

        Optional<EsiSetting> esi = esiSettingRepository.findByTenantId(TENANT_A);
        assertThat(esi).isPresent();
        assertThat(esi.get().isEnabled()).isTrue();
        assertThat(esi.get().getRegistrationNumber()).isEqualTo("31000123450000101");
        assertThat(esi.get().getEmployeeRate()).isEqualByComparingTo("0.7500");
        assertThat(esi.get().getWageCeiling()).isEqualByComparingTo("21000.0000");

        Optional<OrgPtOverride> pt = orgPtOverrideRepository.findByTenantIdAndStateCode(TENANT_A, "KA");
        assertThat(pt).isPresent();
        assertThat(pt.get().getRegistrationNumber()).isEqualTo("29123456789");
        assertThat(pt.get().getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 4, 1));

        // Founder decision 2026-11-24: Entering PT reg number must NOT copy reference slabs to org_pt_override_slab
        var overrideSlabs = orgPtOverrideSlabRepository.findByTenantIdAndOverrideIdOrderBySortOrderAsc(
                TENANT_A, pt.get().getId());
        assertThat(overrideSlabs).isEmpty();

        // Calculate PT: with empty override slabs, it dynamically falls through to reference slabs
        Money ptAmount = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(ptAmount).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("Re-running guided setup preserves existing PT override effective_from date")
    void reRunningGuidedSetupPreservesExistingPtEffectiveFrom() throws Exception {
        // First run seeds PT override with effective date 2024-04-01
        OrgPtOverride initial = new OrgPtOverride(TENANT_A, "KA", "29INITIAL123", LocalDate.of(2024, 4, 1), "system");
        orgPtOverrideRepository.save(initial);

        // Re-run wizard with null or different date in request
        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(false, null, null, false, false, false, false),
                new EsiSetupRequest(false, null, null, false),
                List.of(new PtStateSetupRequest("KA", "29UPDATED999", null)));

        mvc.perform(post("/api/v1/payroll/settings/guided-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Optional<OrgPtOverride> updated = orgPtOverrideRepository.findByTenantIdAndStateCode(TENANT_A, "KA");
        assertThat(updated).isPresent();
        assertThat(updated.get().getRegistrationNumber()).isEqualTo("29UPDATED999");
        assertThat(updated.get().getEffectiveFrom()).isEqualTo(LocalDate.of(2024, 4, 1));
    }
}
