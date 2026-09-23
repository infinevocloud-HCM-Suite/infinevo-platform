package com.infinevo.shared.identity;

import static com.infinevo.shared.identity.IdentityTestSchema.TENANT_ACME;
import static com.infinevo.shared.identity.IdentityTestSchema.TENANT_GLOBEX;
import static com.infinevo.shared.identity.IdentityTestSchema.USER_ADMIN_ACME;
import static com.infinevo.shared.identity.IdentityTestSchema.USER_ADMIN_GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-10 — tenant A cannot read tenant B's {@code core.user_account} row as {@code app_user}
 * (spec section 7).
 *
 * <p>Asserted twice over, because the two halves fail independently: once through the repository,
 * which is how the application reads, and once on a raw {@code app_user} connection, which is the
 * policy in {@code V009__user_account.sql:32-39} on its own with no Java in the way.
 *
 * <p>Unlike {@code core.audit_log}, this table carries ordinary grants — the application reads and
 * writes it — so there is no {@code REVOKE} to assert here.
 */
@SpringBootTest(classes = IdentityTestApp.class)
class UserAccountRlsIT extends AbstractIntegrationTest {

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void applySchema() throws Exception {
        IdentityTestSchema.apply();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        IdentityTestSchema.seedTenants();
        IdentityTestSchema.clearUserAccounts();
        IdentityTestSchema.seedUserAccount(TENANT_ACME, USER_ADMIN_ACME, "admin@acme-payroll.local", "Ana", "Acme");
        IdentityTestSchema.seedUserAccount(
                TENANT_GLOBEX, USER_ADMIN_GLOBEX, "admin@globex-full.local", "Gita", "Globex");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Both rows exist — the control for everything below")
    void bothRowsWereWritten() throws SQLException {
        assertThat(IdentityTestSchema.countUserAccounts(TENANT_ACME)).isEqualTo(1);
        assertThat(IdentityTestSchema.countUserAccounts(TENANT_GLOBEX)).isEqualTo(1);
    }

    @Test
    @DisplayName("Bound to Acme, the repository sees Acme's profile and no other")
    void repositorySeesOnlyTheBoundTenant() {
        TenantContext.set(TENANT_ACME);
        List<UserAccount> rows = transactionTemplate.execute(status -> repository.findAll());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTenantId()).isEqualTo(TENANT_ACME);
        assertThat(rows.get(0).getEmail()).isEqualTo("admin@acme-payroll.local");
    }

    @Test
    @DisplayName("Bound to Acme, a lookup of Globex's user by id finds nothing")
    void lookupOfAnotherTenantsUserFindsNothing() {
        TenantContext.set(TENANT_ACME);
        Optional<UserAccount> other = transactionTemplate.execute(
                status -> repository.findByTenantIdAndKeycloakUserId(TENANT_GLOBEX, USER_ADMIN_GLOBEX));

        assertThat(other).isEmpty();
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant, on a raw app_user connection")
    void rlsHidesTheOtherTenantOnARawConnection() throws SQLException {
        assertThat(visibleRowCountAs(TENANT_ACME)).isEqualTo(1);
        assertThat(visibleRowCountAs(TENANT_GLOBEX)).isEqualTo(1);
    }

    @Test
    @DisplayName("With no tenant bound, app_user sees no profile at all")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = IdentityTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.user_account")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    /** Counts what {@code app_user} can actually see with that tenant bound to the transaction. */
    private static int visibleRowCountAs(java.util.UUID tenantId) throws SQLException {
        try (Connection conn = IdentityTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, tenantId.toString());
                bind.execute();
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.user_account")) {
                rs.next();
                int count = rs.getInt(1);
                conn.commit();
                return count;
            }
        }
    }
}
