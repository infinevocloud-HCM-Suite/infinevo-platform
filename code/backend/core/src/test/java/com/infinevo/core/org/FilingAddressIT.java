package com.infinevo.core.org;

import static com.infinevo.core.org.OrgTestSchema.TENANT_A;
import static com.infinevo.core.org.OrgTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
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

/**
 * W-14.1 — at most one work location per tenant is the filing address (spec section 7 and section 9).
 *
 * <p><strong>A second filing address is refused, not moved.</strong> That decision is recorded on
 * {@link WorkLocationService.FilingAddressAlreadySetException} and is asserted here rather than left
 * to a reader to infer: statutory registrations hang off a filing address (W-31), so silently
 * clearing the flag on location A while the request was about location B would change what A means
 * with nothing in the response saying so. {@link #theFlagIsNotMovedImplicitly()} is the test that
 * pins it, and {@link #clearingTheOldFlagFirstWorks()} is the supported way through.
 *
 * <p>Asserted at both levels, because they hold independently: the service produces a sentence, and
 * the partial unique index at {@code V013__work_location.sql} holds when two requests race past the
 * service's check. {@link #theIndexHoldsOnItsOwn()} goes round the service entirely.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class FilingAddressIT extends AbstractIntegrationTest {

    @Autowired
    private WorkLocationService workLocationService;

    @BeforeAll
    static void applySchema() throws Exception {
        OrgTestSchema.apply();
    }

    /** See {@code OrgMasterRlsIT.cleanUp} — the tenant foreign key makes leftovers another test's failure. */
    @AfterAll
    static void cleanUp() throws SQLException {
        OrgTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        OrgTestSchema.seedTenants();
        OrgTestSchema.clearAll();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The first filing address goes in, address and all")
    void theFirstFilingAddressIsAccepted() {
        TenantContext.set(TENANT_A);

        WorkLocationResponse created = workLocationService.create(filing("HQ", "Head office", true));

        assertThat(created.filingAddress()).isTrue();
        assertThat(created.stateCode()).isEqualTo("KA");
        assertThat(created.countryCode()).isEqualTo("IN");
        assertThat(created.city()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("A second filing address in the same tenant is refused, and the first is untouched")
    void theFlagIsNotMovedImplicitly() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID first =
                workLocationService.create(filing("HQ", "Head office", true)).id();

        assertThatThrownBy(() -> workLocationService.create(filing("BR1", "Branch one", true)))
                .isInstanceOf(WorkLocationService.FilingAddressAlreadySetException.class)
                .hasMessageContaining("HQ")
                .hasMessageContaining("Clear the filing flag");

        assertThat(OrgTestSchema.readColumn("work_location", first, "is_filing_address"))
                .as("the existing filing address must not be demoted by a request about another location")
                .isEqualTo(true);
        assertThat(filingAddressCount(TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("An update cannot promote a second location either")
    void anUpdateCannotPromoteASecondLocation() throws SQLException {
        TenantContext.set(TENANT_A);
        workLocationService.create(filing("HQ", "Head office", true));
        UUID second =
                workLocationService.create(filing("BR1", "Branch one", false)).id();

        assertThatThrownBy(() -> workLocationService.update(second, filing("BR1", "Branch one", true)))
                .isInstanceOf(WorkLocationService.FilingAddressAlreadySetException.class);

        assertThat(filingAddressCount(TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("Clearing the old flag first, then setting the new one, works — the supported path")
    void clearingTheOldFlagFirstWorks() throws SQLException {
        TenantContext.set(TENANT_A);
        UUID first =
                workLocationService.create(filing("HQ", "Head office", true)).id();
        UUID second =
                workLocationService.create(filing("BR1", "Branch one", false)).id();

        workLocationService.update(first, filing("HQ", "Head office", false));
        WorkLocationResponse promoted = workLocationService.update(second, filing("BR1", "Branch one", true));

        assertThat(promoted.filingAddress()).isTrue();
        assertThat(OrgTestSchema.readColumn("work_location", first, "is_filing_address"))
                .isEqualTo(false);
        assertThat(filingAddressCount(TENANT_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("Re-saving the tenant's own filing address is not mistaken for a second one")
    void theFilingAddressCanBeEdited() {
        TenantContext.set(TENANT_A);
        UUID first =
                workLocationService.create(filing("HQ", "Head office", true)).id();

        WorkLocationResponse renamed = workLocationService.update(first, filing("HQ", "Registered office", true));

        assertThat(renamed.name()).isEqualTo("Registered office");
        assertThat(renamed.filingAddress()).isTrue();
    }

    @Test
    @DisplayName("Each tenant gets its own filing address — the cap is per tenant, not global")
    void eachTenantMayHaveOne() throws SQLException {
        TenantContext.set(TENANT_A);
        workLocationService.create(filing("HQ", "Head office", true));

        TenantContext.set(TENANT_B);
        workLocationService.create(filing("HQ", "Sede", true));

        assertThat(filingAddressCount(TENANT_A)).isEqualTo(1);
        assertThat(filingAddressCount(TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("The partial index refuses the second one with the service out of the way")
    void theIndexHoldsOnItsOwn() throws SQLException {
        OrgTestSchema.seedWorkLocation(TENANT_A, "HQ", "Head office", true);

        // As the owner, so this is the index refusing and not row-level security.
        assertThatThrownBy(() -> OrgTestSchema.seedWorkLocation(TENANT_A, "BR1", "Branch one", true))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("idx_work_location_tenant_filing_address");

        // ... while any number of non-filing locations go in, which a plain UNIQUE (tenant_id,
        // is_filing_address) would have capped at one.
        assertThat(OrgTestSchema.seedWorkLocation(TENANT_A, "BR2", "Branch two", false))
                .isNotNull();
        assertThat(OrgTestSchema.seedWorkLocation(TENANT_A, "BR3", "Branch three", false))
                .isNotNull();
        assertThat(OrgTestSchema.countFor("work_location", TENANT_A)).isEqualTo(3);
    }

    @Test
    @DisplayName("A country code that is not two letters is a field error, not a padded CHAR(2)")
    void aBadCountryCodeIsRefused() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> workLocationService.create(new WorkLocationRequest(
                        "HQ", "Head office", null, null, null, null, null, null, "IND", false, null)))
                .isInstanceOf(OrgMasterService.ValidationException.class)
                .hasMessageContaining("countryCode");
    }

    private static WorkLocationRequest filing(String code, String name, boolean filingAddress) {
        return new WorkLocationRequest(
                code,
                name,
                "1 Industrial Layout",
                null,
                "Bengaluru",
                "Karnataka",
                "KA",
                "560001",
                "in",
                filingAddress,
                null);
    }

    /** Counts the tenant's filing addresses as the schema owner — the control the assertions rest on. */
    private static int filingAddressCount(UUID tenantId) throws SQLException {
        try (Connection conn = OrgTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.work_location WHERE tenant_id = ? AND is_filing_address")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
