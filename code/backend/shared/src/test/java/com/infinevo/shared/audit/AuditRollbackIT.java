package com.infinevo.shared.audit;

import static com.infinevo.shared.audit.AuditTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-22.1 — a rolled-back transaction leaves zero audit rows (spec sections 4 and 7).
 *
 * <p>This is the test that makes "post-commit, not pre-commit" mean something. Writing the row
 * from inside the Hibernate event would record a change that never happened.
 */
@SpringBootTest(classes = AuditTestApp.class)
class AuditRollbackIT extends AbstractIntegrationTest {

    @Autowired
    private AuditedProbeRepository probes;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void applySchema() throws Exception {
        AuditTestSchema.apply();
    }

    @BeforeEach
    void reset() throws Exception {
        TenantContext.clear();
        AuditTestSchema.seedTenants();
        AuditTestSchema.clearAll();
    }

    @Test
    @DisplayName("A rolled-back insert leaves zero audit rows and zero business rows")
    void rolledBackTransactionLeavesNoAuditRow() throws Exception {
        TenantContext.set(TENANT_A);
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        probes.saveAndFlush(new AuditedProbe(TENANT_A, "Rolled back", new BigDecimal("1.0000"), "tok"));
                        throw new IllegalStateException("deliberate failure after the write");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("deliberate failure after the write");
        } finally {
            TenantContext.clear();
        }

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isZero();
        assertThat(countProbes()).isZero();
    }

    @Test
    @DisplayName("A committed insert in the same test class does write a row, so the assertion above is not vacuous")
    void committedTransactionDoesWriteARow() throws Exception {
        TenantContext.set(TENANT_A);
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status ->
                            probes.save(new AuditedProbe(TENANT_A, "Committed", new BigDecimal("2.0000"), "tok")));
        } finally {
            TenantContext.clear();
        }

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isEqualTo(1);
    }

    private int countProbes() throws Exception {
        try (Connection conn = AuditTestSchema.migrationConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT count(*) FROM core.audited_probe WHERE tenant_id = '" + TENANT_A + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
