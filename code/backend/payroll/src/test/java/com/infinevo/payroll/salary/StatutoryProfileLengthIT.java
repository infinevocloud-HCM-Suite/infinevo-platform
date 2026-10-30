package com.infinevo.payroll.salary;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-75.1 §7 (was D-132) — an over-long UAN on {@code PUT …/statutory-profile} is a {@code 400} naming {@code uan},
 * never a {@code 500} from the {@code VARCHAR(16)} column, through the real controller and service on Postgres.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class StatutoryProfileLengthIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/payroll/employees/{employeeId}/statutory-profile";

    @Autowired
    private EmployeeStatutoryProfileService statutoryProfileService;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockMvc mvc;
    private UUID employeeId;

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
        employeeId = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, 'EMP-LEN-01', 'Uma', 'Nair', 'emp-len-01@example.com', '2026-01-01', 'ACTIVE')")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, TENANT_A);
            ps.executeUpdate();
        }
        TenantContext.set(TENANT_A);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeStatutoryProfileController(statutoryProfileService))
                .setMessageConverters(converter)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A 17-character UAN is 400 naming uan, and no row is written")
    void seventeenCharacterUanIs400() throws Exception {
        StatutoryProfileRequest request =
                new StatutoryProfileRequest(true, false, false, false, true, false, false, null, "1".repeat(17), null);

        mvc.perform(put(URL, employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.uan").value("UAN must be at most 16 characters"));

        assertThat(countProfiles()).isZero();
    }

    @Test
    @DisplayName("A 16-character UAN with surrounding spaces saves trimmed")
    void sixteenCharacterUanSaves() throws Exception {
        StatutoryProfileRequest request = new StatutoryProfileRequest(
                true, false, false, false, true, false, false, " ", "  " + "1".repeat(16) + " ", null);

        mvc.perform(put(URL, employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uan").value("1".repeat(16)))
                .andExpect(jsonPath("$.pfAccountNumber").doesNotExist());

        assertThat(countProfiles()).isEqualTo(1);
    }

    private static int countProfiles() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM payroll.employee_statutory_profile");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
