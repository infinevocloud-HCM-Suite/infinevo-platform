package com.infinevo.core.org;

import com.infinevo.core.employee.EmployeeRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The work location service (W-14.1) — the one master that is not a copy of the other two.
 *
 * <p>Two things are added to {@link AbstractOrgMasterServiceImpl}: the address, and the rule that a
 * tenant has at most one filing address.
 *
 * <p><strong>A second filing address is refused, not moved.</strong> The alternative — clearing the
 * flag on the existing location automatically — was rejected, and the reason is in
 * {@link WorkLocationService.FilingAddressAlreadySetException}: statutory registrations hang off a
 * filing address (W-31), so a request about location B would silently change what location A means,
 * with nothing in the response to say so. The caller clears the old flag first, in a step it can see.
 *
 * <p>The check is made twice on purpose, once here and once by the partial unique index at
 * {@code V013__work_location.sql}. The check produces a sentence a user can act on; the index holds
 * when two requests race, which the check cannot. {@link #translateIntegrityViolation} makes the
 * second one read like the first.
 */
@Service
public class WorkLocationServiceImpl
        extends AbstractOrgMasterServiceImpl<WorkLocation, WorkLocationRequest, WorkLocationResponse>
        implements WorkLocationService {

    /** The unique index from {@code V013__work_location.sql}. See {@link DepartmentServiceImpl}. */
    private static final String CODE_INDEX = "idx_work_location_tenant_code";

    /** The partial unique index that caps a tenant at one filing address — {@code V013__work_location.sql}. */
    private static final String FILING_ADDRESS_INDEX = "idx_work_location_tenant_filing_address";

    private static final int MAX_ADDRESS_LINE = 255;
    private static final int MAX_CITY = 100;
    private static final int MAX_STATE = 100;
    private static final int MAX_STATE_CODE = 8;
    private static final int MAX_ZIP_CODE = 16;
    private static final int COUNTRY_CODE_LENGTH = 2;

    private final WorkLocationRepository workLocationRepository;
    private final EmployeeRepository employeeRepository;

    public WorkLocationServiceImpl(
            WorkLocationRepository workLocationRepository, EmployeeRepository employeeRepository) {
        super(workLocationRepository);
        this.workLocationRepository =
                Objects.requireNonNull(workLocationRepository, "workLocationRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    protected String kind() {
        return "work location";
    }

    @Override
    protected String uniqueCodeIndexName() {
        return CODE_INDEX;
    }

    @Override
    protected WorkLocation newEntity(UUID tenantId, String actor) {
        return new WorkLocation(tenantId, actor);
    }

    @Override
    protected WorkLocationResponse toResponse(WorkLocation entity) {
        return WorkLocationResponse.from(entity);
    }

    @Override
    protected long countAssigned(UUID tenantId, UUID id) {
        return employeeRepository.countByTenantIdAndWorkLocation_Id(tenantId, id);
    }

    @Override
    protected void validateExtra(WorkLocationRequest request, Map<String, String> errors) {
        optional(errors, "addressLine1", request.addressLine1(), MAX_ADDRESS_LINE);
        optional(errors, "addressLine2", request.addressLine2(), MAX_ADDRESS_LINE);
        optional(errors, "city", request.city(), MAX_CITY);
        optional(errors, "state", request.state(), MAX_STATE);
        optional(errors, "stateCode", request.stateCode(), MAX_STATE_CODE);
        optional(errors, "zipCode", request.zipCode(), MAX_ZIP_CODE);

        // CHAR(2) in the migration, and ISO 3166-1 alpha-2 in meaning. A one- or three-letter value
        // is not a country code that any filing can use, and CHAR(2) would pad or refuse it rather
        // than say so.
        String countryCode = trimToNull(request.countryCode());
        if (countryCode != null && countryCode.length() != COUNTRY_CODE_LENGTH) {
            errors.put("countryCode", "countryCode must be the 2-letter ISO 3166-1 alpha-2 code");
        }
    }

    @Override
    protected void applyExtra(WorkLocation entity, WorkLocationRequest request) {
        String countryCode = trimToNull(request.countryCode());
        entity.applyAddress(
                trimToNull(request.addressLine1()),
                trimToNull(request.addressLine2()),
                trimToNull(request.city()),
                trimToNull(request.state()),
                trimToNull(request.stateCode()),
                trimToNull(request.zipCode()),
                countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT),
                // Null means "leave it where it is", never "clear it" — the same rule
                // OrgMasterRequest.active and EmployeeRequest.status already follow, and it used to
                // be the opposite here. A PUT that omitted the field silently demoted the tenant's
                // filing address, which is exactly the meaning-change FilingAddressAlreadySetException
                // exists to refuse on the other side: setting B is not allowed to quietly unset A, so
                // omitting a field must not either. Statutory registrations hang off this flag (W-31).
                // Clearing it is still possible and still explicit: send false.
                request.filingAddress() != null ? request.filingAddress() : entity.isFilingAddress());
    }

    /**
     * Refuses a second filing address before the index has to.
     *
     * <p>The location being saved is excluded by id, so re-saving the tenant's existing filing
     * address — a rename, an address correction — is not mistaken for a second one. On a create the
     * id is still null, and no existing row can match it.
     */
    @Override
    protected void checkTenantInvariants(WorkLocationRequest request, UUID id, UUID tenantId) {
        if (request.filingAddress() == null || !request.filingAddress()) {
            return;
        }
        Optional<WorkLocation> existing = workLocationRepository.findByTenantIdAndFilingAddressTrue(tenantId);
        existing.filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new FilingAddressAlreadySetException(other.getCode());
        });
    }

    /** The partial unique index, when two requests race past {@link #beforeSave}. */
    @Override
    protected RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        if (namesIndex(e, FILING_ADDRESS_INDEX)) {
            return new FilingAddressAlreadySetException();
        }
        return e;
    }
}
