package com.infinevo.payroll.proof;

import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.ReminderAnchorResolver;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindowRepository;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies the reference anchor date for {@link Anchor#POI_DUE_DATE} reminder rules (W-34.3).
 *
 * <p>Resolves the income tax declaration window for the financial year containing "today" in
 * {@link TaxDeclarationRules#ZONE}, and returns {@code poi_due_date} if the window is present,
 * not locked, has a due date configured, and has opened ({@code poi_opens_on} set and not after today).
 * Before the window opens submit answers {@code PROOF_WINDOW_CLOSED}, so a reminder then would ask the
 * employee for something they cannot yet do.
 */
@Component
public class ProofDueDateAnchorResolver implements ReminderAnchorResolver {

    private final IncomeTaxDeclarationWindowRepository windowRepository;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    ProofDueDateAnchorResolver(IncomeTaxDeclarationWindowRepository windowRepository, Clock clock) {
        this.windowRepository = windowRepository;
        this.clock = clock;
    }

    @Autowired
    public ProofDueDateAnchorResolver(IncomeTaxDeclarationWindowRepository windowRepository) {
        this(windowRepository, TaxDeclarationRules.defaultClock());
    }

    @Override
    public Anchor anchor() {
        return Anchor.POI_DUE_DATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> resolveAnchorDate(ReminderRule rule, UUID tenantId) {
        LocalDate today = LocalDate.now(clock.withZone(TaxDeclarationRules.ZONE));
        FinancialYear fy = FinancialYear.of(today);
        return windowRepository
                .findByTenantIdAndFinancialYear(tenantId, fy.label())
                .filter(w -> !w.isPoiLocked())
                .filter(w -> w.getPoiOpensOn() != null && !today.isBefore(w.getPoiOpensOn()))
                .map(IncomeTaxDeclarationWindow::getPoiDueDate);
    }
}
