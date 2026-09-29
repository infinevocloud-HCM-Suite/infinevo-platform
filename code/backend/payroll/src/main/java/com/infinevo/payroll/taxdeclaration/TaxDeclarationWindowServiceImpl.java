package com.infinevo.payroll.taxdeclaration;

import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowResponse;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing income tax declaration window settings per financial year (W-32.1).
 */
@Service
@Transactional
public class TaxDeclarationWindowServiceImpl implements TaxDeclarationWindowService {

    private final IncomeTaxDeclarationWindowRepository windowRepository;

    public TaxDeclarationWindowServiceImpl(IncomeTaxDeclarationWindowRepository windowRepository) {
        this.windowRepository = Objects.requireNonNull(windowRepository, "windowRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public TaxDeclarationWindowResponse get(String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        return windowRepository
                .findByTenantIdAndFinancialYear(tenantId, fy.label())
                .map(w -> toResponse(w, true))
                .orElseGet(() -> defaultResponse(fy));
    }

    @Override
    public TaxDeclarationWindowResponse upsert(String financialYear, TaxDeclarationWindowRequest request) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        if (request == null) {
            throw new WindowValidationException("Request body must not be null");
        }

        LocalDate opens = request.windowOpensOn();
        LocalDate closes = request.windowClosesOn();
        if (opens == null || closes == null) {
            throw new WindowValidationException("Window opening and closing dates are required");
        }
        if (closes.isBefore(opens)) {
            throw new WindowValidationException("Window closing date cannot be before opening date");
        }
        if (opens.isBefore(fy.start()) || opens.isAfter(fy.end())) {
            throw new WindowValidationException("Window opening date must fall within financial year " + fy.label());
        }
        if (closes.isBefore(fy.start()) || closes.isAfter(fy.end())) {
            throw new WindowValidationException("Window closing date must fall within financial year " + fy.label());
        }

        String regime = request.defaultTaxRegime();
        if (regime != null && !regime.equals("OLD") && !regime.equals("NEW")) {
            throw new WindowValidationException("Default tax regime must be OLD or NEW");
        }

        UUID tenantId = TenantContext.require();
        IncomeTaxDeclarationWindow window = windowRepository
                .findByTenantIdAndFinancialYear(tenantId, fy.label())
                .orElseGet(() -> new IncomeTaxDeclarationWindow(
                        tenantId,
                        fy.label(),
                        opens,
                        closes,
                        regime != null ? regime : "NEW",
                        request.canChangeTaxRegime() != null ? request.canChangeTaxRegime() : true,
                        request.panRequiredForRentOverThreshold() != null
                                ? request.panRequiredForRentOverThreshold()
                                : true));

        window.setWindowOpensOn(opens);
        window.setWindowClosesOn(closes);
        if (request.isLocked() != null) {
            window.setLocked(request.isLocked());
        }
        if (regime != null) {
            window.setDefaultTaxRegime(regime);
        }
        if (request.canChangeTaxRegime() != null) {
            window.setCanChangeTaxRegime(request.canChangeTaxRegime());
        }
        if (request.panRequiredForRentOverThreshold() != null) {
            window.setPanRequiredForRentOverThreshold(request.panRequiredForRentOverThreshold());
        }
        if (request.notifyOnLock() != null) {
            window.setNotifyOnLock(request.notifyOnLock());
        }
        if (request.notifyOnRelease() != null) {
            window.setNotifyOnRelease(request.notifyOnRelease());
        }

        window = windowRepository.save(window);
        return toResponse(window, true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOpen(String financialYear, LocalDate today) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        return findOrCreateDefault(tenantId, fy.label()).isOpenOn(today);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IncomeTaxDeclarationWindow> find(UUID tenantId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        return windowRepository.findByTenantIdAndFinancialYear(tenantId, fy.label());
    }

    @Override
    public IncomeTaxDeclarationWindow findOrCreateDefault(UUID tenantId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        return windowRepository
                .findByTenantIdAndFinancialYear(tenantId, fy.label())
                .orElseGet(() -> {
                    IncomeTaxDeclarationWindow def = new IncomeTaxDeclarationWindow(
                            tenantId, fy.label(), fy.start(), fy.end(), "NEW", true, true);
                    def.setLocked(true);
                    return def;
                });
    }

    private TaxDeclarationWindowResponse toResponse(IncomeTaxDeclarationWindow w, boolean exists) {
        return new TaxDeclarationWindowResponse(
                w.getId(),
                w.getFinancialYear(),
                w.getWindowOpensOn(),
                w.getWindowClosesOn(),
                w.isLocked(),
                w.getDefaultTaxRegime(),
                w.isCanChangeTaxRegime(),
                w.isPanRequiredForRentOverThreshold(),
                w.isNotifyOnLock(),
                w.isNotifyOnRelease(),
                exists,
                w.isOpenOn(TaxDeclarationRules.today(TaxDeclarationRules.defaultClock())));
    }

    private TaxDeclarationWindowResponse defaultResponse(FinancialYear fy) {
        return new TaxDeclarationWindowResponse(
                null, fy.label(), fy.start(), fy.end(), true, "NEW", true, true, false, false, false, false);
    }
}
