package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-19 §7 — row-level security on {@code core.pay_input} and {@code core.pay_input_period_lock}, as
 * {@code app_user}.
 *
 * <p>Whether a row can be updated or deleted at all is {@link PayInputImmutabilityIT}'s question;
 * this class asks only whether a tenant can read or insert across another tenant's boundary.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputRlsIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;
    private UUID payInputIdOfA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = PayInputTestSchema.insertTenant("RLS A " + UUID.randomUUID());
        tenantB = PayInputTestSchema.insertTenant("RLS B " + UUID.randomUUID());
        employeeA = PayInputTestSchema.insertEmployee(tenantA, "RLS-A-" + UUID.randomUUID());
        employeeB = PayInputTestSchema.insertEmployee(tenantB, "RLS-B-" + UUID.randomUUID());

        TenantContext.set(tenantA);
        payInputIdOfA = payInputService
                .record(new PayInputCommand(
                        employeeA, YearMonth.of(2026, 4), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "rls-a"))
                .id();
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant B cannot read tenant A's pay input; tenant A can")
    void tenantBCannotReadTenantAsRow() throws SQLException {
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenantB);
            assertThat(countRow(conn, payInputIdOfA)).isZero();
        }
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenantA);
            assertThat(countRow(conn, payInputIdOfA)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Bound to tenant B, an insert naming tenant A is rejected by the RLS policy")
    void crossTenantInsertIsRejected() throws SQLException {
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, quantity, source_module, source_ref)"
                    + " VALUES (?, ?, '2026-04', 'LOP_DAYS', 1, 'test', 'rls-cross')")) {
                ps.setObject(1, tenantA);
                ps.setObject(2, employeeA);
                assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("Tenant A's lock does not stop tenant B's insert for the same calendar period")
    void oneTenantsLockDoesNotAffectAnother() throws SQLException {
        TenantContext.set(tenantA);
        payInputService.lock(YearMonth.of(2026, 7));
        TenantContext.clear();

        TenantContext.set(tenantB);
        PayInputResponse response = payInputService.record(new PayInputCommand(
                employeeB, YearMonth.of(2026, 7), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "rls-b-open"));
        TenantContext.clear();

        assertThat(response.postedPeriod()).isEqualTo(YearMonth.of(2026, 7));
    }

    @Test
    @DisplayName("W-30.1: tenant A's forRun(runRef) never returns tenant B's rows for the same run_ref value")
    void forRunNeverCrossesTenants() {
        UUID sharedRunRef = UUID.randomUUID(); // both tenants happen to use the same value

        TenantContext.set(tenantA);
        payInputService.record(new PayInputCommand(
                employeeA,
                YearMonth.of(2026, 4),
                PayInputKind.ONE_TIME_PAYOUT,
                null,
                com.infinevo.shared.money.Money.of("500"),
                "payroll",
                "rls-run-a",
                sharedRunRef));
        TenantContext.clear();

        TenantContext.set(tenantB);
        payInputService.record(new PayInputCommand(
                employeeB,
                YearMonth.of(2026, 4),
                PayInputKind.ONE_TIME_PAYOUT,
                null,
                com.infinevo.shared.money.Money.of("750"),
                "payroll",
                "rls-run-b",
                sharedRunRef));

        PayInputRunResponse response = payInputService.forRun(sharedRunRef);
        TenantContext.clear();

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).employeeId()).isEqualTo(employeeB);
    }

    private static long countRow(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.pay_input WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
