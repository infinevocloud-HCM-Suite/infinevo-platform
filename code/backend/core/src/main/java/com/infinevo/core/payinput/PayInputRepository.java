package com.infinevo.core.payinput;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.pay_input} (W-19). Every read names the tenant, and {@code save} is
 * the only write this repository is ever asked for — {@code app_user} holds no {@code UPDATE} or
 * {@code DELETE} on the table ({@code V031}), so there is nothing here to update or delete by.
 */
@Transactional(readOnly = true)
public interface PayInputRepository extends JpaRepository<PayInput, UUID> {

    /** One row in the bound tenant, for {@link PayInputService#reverse}. */
    Optional<PayInput> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * One employee's untagged rows for a period — {@code forEmployee}, served by the
     * tenant+employee+period index. Excludes a run's tagged rows (W-30.1 spec §4): a bonus paid
     * off-cycle must not also be read, and so paid again, by the regular run.
     */
    List<PayInput> findByTenantIdAndEmployeeIdAndPeriodAndRunRefIsNull(
            UUID tenantId, UUID employeeId, YearMonth period);

    /**
     * Every employee's untagged rows for a period, in one statement — the batch read the pay run
     * needs (spec §4), not one query per employee. Excludes a run's tagged rows, the same reason.
     */
    List<PayInput> findByTenantIdAndPeriodAndRunRefIsNull(UUID tenantId, YearMonth period);

    /** Every row tagged to one run, all employees, one statement — the batch read W-30.2 needs. */
    List<PayInput> findByTenantIdAndRunRef(UUID tenantId, UUID runRef);

    /**
     * The rows a module wrote under the given references, in one statement — how W-73.6's scheduled
     * earnings find the pay input each instalment became, and check one is not written twice.
     * Reversals are included; a caller that wants originals only filters on {@code reversesId}.
     */
    List<PayInput> findByTenantIdAndSourceModuleAndSourceRefIn(
            UUID tenantId, String sourceModule, Collection<String> sourceRefs);

    /**
     * One employee's rows a module wrote under references starting with {@code sourceRefPrefix}, in one
     * statement — the history of W-73.6's scheduled instalments, whichever months they landed in.
     */
    List<PayInput> findByTenantIdAndEmployeeIdAndSourceModuleAndSourceRefStartingWith(
            UUID tenantId, UUID employeeId, String sourceModule, String sourceRefPrefix);

    /** Whether a reversal of this row already exists — {@code reverse} refuses a second one. */
    boolean existsByTenantIdAndReversesId(UUID tenantId, UUID reversesId);
}
