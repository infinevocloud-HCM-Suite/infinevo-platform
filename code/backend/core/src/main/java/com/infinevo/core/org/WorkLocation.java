package com.infinevo.core.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One physical work location in one tenant (W-14.1) — {@code core.work_location},
 * {@code migration/src/main/resources/db/migration/core/V013__work_location.sql}.
 *
 * <p>The third master, and the one that is not a copy of the other two: it carries a full address
 * and the statutory filing flag, which is the shape Payroll already models
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java}).
 *
 * <p><strong>{@code stateCode} is a separate field from {@code state} and is not derived from it.</strong>
 * Payroll filings need the state's code, and free text cannot supply one: "Karnataka", "karnataka"
 * and "KA " are the same state to a human and three different values to a filing. The same reasoning
 * settled the address shape in W-13.2 — spec section 6.
 *
 * <p><strong>At most one filing address per tenant.</strong> The database holds that with a partial
 * unique index on {@code (tenant_id) WHERE is_filing_address} ({@code V013__work_location.sql}), and
 * {@link WorkLocationServiceImpl} refuses the second one with a sentence rather than letting the
 * constraint surface. The partial index is what makes it work: only the filing rows take part, so a
 * tenant may hold any number of ordinary locations and exactly one that files.
 *
 * <p>The statutory registrations that hang off a filing address are W-31, not this ticket.
 */
@Entity
@Table(
        name = "work_location",
        schema = "core",
        indexes = {
            @Index(name = "idx_work_location_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_work_location_tenant_is_active", columnList = "tenant_id, is_active")
        })
public class WorkLocation extends OrgMaster {

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    /** The filing code, never derived from {@link #state}. See the class comment. */
    @Column(name = "state_code", length = 8)
    private String stateCode;

    @Column(name = "zip_code", length = 16)
    private String zipCode;

    /** ISO 3166-1 alpha-2, fixed width by definition — {@code CHAR(2)} in the migration. */
    @Column(name = "country_code", length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "is_filing_address", nullable = false)
    private boolean filingAddress = false;

    protected WorkLocation() {}

    WorkLocation(UUID tenantId, String actor) {
        super(tenantId, actor);
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public boolean isFilingAddress() {
        return filingAddress;
    }

    /**
     * Copies the address and the filing flag in.
     *
     * <p>Separate from {@link OrgMaster#apply}, and package-private for the same reason: the common
     * fields are the superclass's business and these are this class's. Called only by
     * {@link WorkLocationServiceImpl}, after validation.
     */
    void applyAddress(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String stateCode,
            String zipCode,
            String countryCode,
            boolean filingAddress) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.stateCode = stateCode;
        this.zipCode = zipCode;
        this.countryCode = countryCode;
        this.filingAddress = filingAddress;
    }
}
