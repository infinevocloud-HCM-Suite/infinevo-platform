package com.infinevo.core.lop;

import static com.infinevo.core.lop.LopTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-18.1 §7 — effective-date versioning of {@code core.lop_policy} against real PostgreSQL: the
 * repository's "policy in force" query, the immutability of a saved version, and the
 * {@code (tenant_id, effective_from)} unique key from {@code V119}.
 */
@SpringBootTest(classes = LopTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class LopPolicyVersionIT extends AbstractIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);

    @Autowired
    private LopPolicyService service;

    @BeforeAll
    static void applySchema() throws Exception {
        LopTestSchema.apply();
        LopTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LopTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Removes Tenant A's seeded 1900 row too, so only the versions each test writes exist.
        LopTestSchema.clearAll();
        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The policy in force is the latest version on or before the date; March is unchanged by April")
    void twoVersions_resolveByDate_andMarchStaysIntact() throws SQLException {
        LopPolicyResponse march = service.savePolicy(
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, MARCH));
        Row marchBefore = readRow(march.id());

        LopPolicyResponse april = service.savePolicy(new LopPolicyRequest(
                WorkingDayBasis.ORG_DAYS, new BigDecimal("26.00"), true, false, LopRounding.HALF_UP_0, APRIL));

        // Before the first version: nothing in force
        assertThatThrownBy(() -> service.getPolicyInForce(LocalDate.of(2026, 2, 28)))
                .isInstanceOf(NoLopPolicyException.class);

        // Before the second effective_from: March
        LopPolicyResponse lastDayOfMarch = service.getPolicyInForce(LocalDate.of(2026, 3, 31));
        assertThat(lastDayOfMarch.id()).isEqualTo(march.id());
        assertThat(lastDayOfMarch.workingDayBasis()).isEqualTo(WorkingDayBasis.FIXED_30);

        // On the second effective_from: April
        LopPolicyResponse firstOfApril = service.getPolicyInForce(APRIL);
        assertThat(firstOfApril.id()).isEqualTo(april.id());
        assertThat(firstOfApril.configuredDaysPerMonth()).isEqualByComparingTo("26.00");

        // After it: still April
        assertThat(service.getPolicyInForce(LocalDate.of(2026, 7, 15)).id()).isEqualTo(april.id());

        // The March row in the database is exactly what it was before April was added
        Row marchAfter = readRow(march.id());
        assertThat(marchAfter).isEqualTo(marchBefore);
        assertThat(marchAfter.basis()).isEqualTo("FIXED_30");
        assertThat(marchAfter.effectiveFrom()).isEqualTo(MARCH);
    }

    @Test
    @DisplayName("F-3: two saves for one effective date with different settings — the second is refused")
    void secondSaveForSameDate_isRefused() throws SQLException {
        service.savePolicy(
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, MARCH));

        assertThatThrownBy(() -> service.savePolicy(new LopPolicyRequest(
                        WorkingDayBasis.ACTUAL_DAYS, null, true, true, LopRounding.HALF_UP_2, MARCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Policy versions are immutable");

        assertThat(countRows(MARCH)).isEqualTo(1);
        assertThat(service.getPolicyInForce(MARCH).workingDayBasis()).isEqualTo(WorkingDayBasis.FIXED_30);
    }

    @Test
    @DisplayName("F-3: repeating the identical save is idempotent and still leaves one row")
    void identicalSaveForSameDate_isIdempotent() throws SQLException {
        LopPolicyRequest request =
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, MARCH);
        LopPolicyResponse first = service.savePolicy(request);
        LopPolicyResponse second = service.savePolicy(request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countRows(MARCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-3: the database refuses a second row for the same tenant and effective date (V119)")
    void duplicateEffectiveFrom_refusedByUniqueKey() throws SQLException {
        service.savePolicy(
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, MARCH));

        try (Connection conn = LopTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, effective_from)"
                                + " VALUES (?, ?, 'ACTUAL_DAYS', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_A);
            ps.setObject(3, MARCH);

            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_lop_policy_tenant_effective_from");
        }
    }

    @Test
    @DisplayName("F-5: a request with no rounding is stored as HALF_UP_2")
    void nullRounding_storedAsHalfUp2() throws SQLException {
        LopPolicyResponse saved =
                service.savePolicy(new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, null, null, null, MARCH));

        assertThat(saved.lopRounding()).isEqualTo(LopRounding.HALF_UP_2);
        assertThat(readRow(saved.id()).rounding()).isEqualTo("HALF_UP_2");
    }

    @Test
    @DisplayName("When effectiveFrom is null, the version starts today")
    void nullEffectiveFrom_defaultsToToday() {
        LopPolicyResponse saved = service.savePolicy(
                new LopPolicyRequest(WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, null));

        assertThat(saved.effectiveFrom()).isEqualTo(LocalDate.now());
    }

    private record Row(
            String basis,
            BigDecimal configured,
            boolean weekends,
            boolean holidays,
            String rounding,
            LocalDate effectiveFrom,
            Timestamp updatedAt) {}

    private static Row readRow(UUID id) throws SQLException {
        try (Connection conn = LopTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT working_day_basis, configured_days_per_month, weekends_payable, holidays_payable,"
                                + " lop_rounding, effective_from, updated_at FROM core.lop_policy WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return new Row(
                        rs.getString(1),
                        rs.getBigDecimal(2),
                        rs.getBoolean(3),
                        rs.getBoolean(4),
                        rs.getString(5),
                        rs.getObject(6, LocalDate.class),
                        rs.getTimestamp(7));
            }
        }
    }

    private static int countRows(LocalDate effectiveFrom) throws SQLException {
        try (Connection conn = LopTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.lop_policy WHERE tenant_id = ? AND effective_from = ?")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, effectiveFrom);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
