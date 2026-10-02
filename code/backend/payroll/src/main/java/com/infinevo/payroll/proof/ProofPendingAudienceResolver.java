package com.infinevo.payroll.proof;

import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the {@code POI_PENDING} audience into recipient employee IDs for a given tenant (W-34.3).
 *
 * <p>Resolves active employees in the tenant who have submitted an investment declaration for the
 * financial year containing "today" in {@link TaxDeclarationRules#ZONE}, but who either have not
 * started their proof of investment or have it in {@code DRAFT} or {@code REJECTED} status.
 */
@Component
public class ProofPendingAudienceResolver implements ReminderAudienceResolver {

    public static final String AUDIENCE = "POI_PENDING";

    private final ProofPendingQuery proofPendingQuery;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    ProofPendingAudienceResolver(ProofPendingQuery proofPendingQuery, Clock clock) {
        this.proofPendingQuery = Objects.requireNonNull(proofPendingQuery, "proofPendingQuery must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Autowired
    public ProofPendingAudienceResolver(ProofPendingQuery proofPendingQuery) {
        this(proofPendingQuery, TaxDeclarationRules.defaultClock());
    }

    @Override
    public String audience() {
        return AUDIENCE;
    }

    /**
     * Resolves the pending recipient employee IDs in the given tenant.
     *
     * <p>Opens its own read-only transaction: the reminder sweep calls audience resolvers with the tenant
     * bound and no transaction open, and a read on an auto-commit connection is refused.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
        LocalDate today = LocalDate.now(clock.withZone(TaxDeclarationRules.ZONE));
        FinancialYear fy = FinancialYear.of(today);
        return proofPendingQuery.findPendingEmployeeIds(tenantId, fy.label());
    }
}
