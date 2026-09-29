package com.infinevo.payroll.statutory.pt;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying that states listed for a tenant match active work locations (W-31.2, spec section 7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ProfessionalTaxStatesIT extends AbstractIntegrationTest {

    @Autowired
    private ProfessionalTaxService professionalTaxService;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (tenant_id, code, name, state, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            // 1. Active location in Karnataka (KA levies PT)
            ps.setObject(1, TENANT_A);
            ps.setString(2, "BLR");
            ps.setString(3, "Bangalore HQ");
            ps.setString(4, "Karnataka");
            ps.setString(5, "KA");
            ps.setBoolean(6, true);
            ps.executeUpdate();

            // 2. Active location in Delhi (DL does not levy PT)
            ps.setObject(1, TENANT_A);
            ps.setString(2, "DEL");
            ps.setString(3, "Delhi Branch");
            ps.setString(4, "Delhi");
            ps.setString(5, "DL");
            ps.setBoolean(6, true);
            ps.executeUpdate();

            // 3. INACTIVE location in Maharashtra (MH)
            ps.setObject(1, TENANT_A);
            ps.setString(2, "PUN");
            ps.setString(3, "Pune Branch");
            ps.setString(4, "Maharashtra");
            ps.setString(5, "MH");
            ps.setBoolean(6, false); // inactive!
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("States listed are exactly active locations; inactive state absent; non-levying state shows NONE")
    void activeWorkLocationStateFilter() {
        TenantContext.set(TENANT_A);

        List<PtStateResponse> states = professionalTaxService.statesForTenant();

        // Exactly 2 active states (KA, DL), inactive MH is absent
        assertThat(states).extracting(PtStateResponse::stateCode).containsExactlyInAnyOrder("KA", "DL");

        // Karnataka levies PT -> REFERENCE
        PtStateResponse ka = states.stream()
                .filter(s -> "KA".equals(s.stateCode()))
                .findFirst()
                .orElseThrow();
        assertThat(ka.source()).isEqualTo(PtSource.REFERENCE);
        assertThat(ka.slabs()).isNotEmpty();

        // Delhi does not levy PT -> NONE
        PtStateResponse dl = states.stream()
                .filter(s -> "DL".equals(s.stateCode()))
                .findFirst()
                .orElseThrow();
        assertThat(dl.source()).isEqualTo(PtSource.NONE);
        assertThat(dl.slabs()).isEmpty();

        // Direct request for inactive MH returns 404
        assertThatThrownBy(() -> professionalTaxService.getStateForTenant("MH"))
                .isInstanceOf(PtNotFoundException.class)
                .hasMessageContaining("No active work location found in state MH");
    }
}
