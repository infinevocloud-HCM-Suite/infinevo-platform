package com.infinevo.app.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.payroll.payrun.PayRunController;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.payrun.bankfile.BankFileResult;
import com.infinevo.payroll.payrun.bankfile.BankFileService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-88 / Point 9: Integration test that boots the Spring application context with component scan
 * across PayRunController. Proves that Spring container initializes PayRunController via its single
 * constructor (catching Point 1 "No default constructor found") and verifies the bank-file endpoint.
 */
@SpringBootTest(
        classes = BankFileExportEndpointIT.TestApp.class,
        properties = {
            "KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/infinevo",
            "KEYCLOAK_JWK_SET_URI=http://localhost:8081/realms/infinevo/protocol/openid-connect/certs",
            "DB_URL=jdbc:postgresql://localhost:5432/test",
            "DB_USERNAME=test",
            "DB_PASSWORD=test"
        })
@AutoConfigureMockMvc(addFilters = false)
class BankFileExportEndpointIT {

    @SpringBootApplication(
            scanBasePackages = {"com.infinevo.payroll.payrun"},
            exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                ManagementWebSecurityAutoConfiguration.class
            })
    static class TestApp {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayRunController payRunController; // Proves Spring container cleanly bootstrapped PayRunController

    @MockBean
    private PayRunService payRunService;

    @MockBean
    private BankFileService bankFileService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private EntitlementService entitlementService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        given(permissionService.holds("payroll.bank_file.export")).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Calls bank-file endpoint through the running Spring container; verifies single constructor autowiring")
    void exportBankFile_throughRunningApp_returns200AndMetadataHeaders() throws Exception {
        UUID payrunId = UUID.randomUUID();
        PayRunResponse payRun = new PayRunResponse(
                payrunId,
                "2026-11",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 11, 28),
                LocalDate.of(2026, 11, 30),
                PayRunType.REGULAR,
                PayRunStatus.APPROVED,
                5,
                0,
                BigDecimal.valueOf(100000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(100000),
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                0,
                Instant.now(),
                Instant.now());

        byte[] csv = "Sr No,Employee Number,Net Pay (INR)\n1,EMP001,100000.00".getBytes();
        BankFileResult result = new BankFileResult(
                csv,
                "bank_salary_file_2026-11.csv",
                5,
                5,
                0,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(100000),
                List.of());

        given(payRunService.get(payrunId)).willReturn(payRun);
        given(bankFileService.generate(payrunId)).willReturn(result);

        mockMvc.perform(get("/api/v1/payroll/payruns/{id}/bank-file", payrunId))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Content-Disposition", "attachment; filename=\"bank_salary_file_2026-11.csv\""))
                .andExpect(header().string("X-BankFile-Total-Employees", "5"))
                .andExpect(header().string("X-BankFile-Exported-Employees", "5"))
                .andExpect(header().string("X-BankFile-Omitted-Employees", "0"))
                .andExpect(header().string("X-BankFile-File-Total", "100000"))
                .andExpect(header().string("X-BankFile-Run-Net-Pay", "100000"));
    }
}
