package com.infinevo.payroll.tds;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeTdsService} (W-36.1 §4).
 */
@Service
public class EmployeeTdsServiceImpl implements EmployeeTdsService {

    private final EmployeeTdsRepository repository;
    private final EmployeePayRunLineRepository lineRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public EmployeeTdsServiceImpl(
            EmployeeTdsRepository repository,
            EmployeePayRunLineRepository lineRepository,
            EmployeeService employeeService) {
        this(repository, lineRepository, employeeService, EmployeeTdsRules.defaultClock());
    }

    public EmployeeTdsServiceImpl(
            EmployeeTdsRepository repository,
            EmployeePayRunLineRepository lineRepository,
            EmployeeService employeeService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.lineRepository = Objects.requireNonNull(lineRepository, "lineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.clock = clock != null ? clock : EmployeeTdsRules.defaultClock();
    }

    @Override
    @Transactional
    public EmployeeTdsResponse record(UUID employeeId, String financialYear, TdsFigures figures) {
        TdsSource source =
                figures != null && figures.declarationId() != null ? TdsSource.DECLARATION : TdsSource.OFFICER;
        return record(employeeId, financialYear, figures, source);
    }

    @Override
    @Transactional
    public EmployeeTdsResponse record(UUID employeeId, String financialYear, TdsFigures figures, TdsSource source) {
        UUID tenantId = TenantContext.require();
        if (employeeId == null) {
            throw new EmployeeTdsValidationException("employeeId must not be null");
        }
        if (source == null) {
            source = TdsSource.OFFICER;
        }

        // Validate employee belongs to tenant
        employeeService.get(employeeId);

        // Validate financial year
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear(financialYear);

        // Validate figures
        EmployeeTdsRules.validateFigures(figures, fy);

        String effectivePeriod = figures.effectiveFromPeriod();
        if (effectivePeriod == null || effectivePeriod.isBlank()) {
            YearMonth nowYm = YearMonth.now(clock);
            effectivePeriod = nowYm.toString();
        } else {
            effectivePeriod = effectivePeriod.trim();
        }

        Instant now = Instant.now();
        String actor = currentActor();

        // Supersede active row if present
        Optional<EmployeeTds> existingActive = repository.findActive(tenantId, employeeId, fy.label());
        if (existingActive.isPresent()) {
            EmployeeTds activeRow = existingActive.get();
            activeRow.setActive(false);
            activeRow.setSupersededAt(now);
            activeRow.setUpdatedAt(now);
            activeRow.setUpdatedBy(actor);
            repository.saveAndFlush(activeRow);
        }

        // Insert new active row
        EmployeeTds newRow = new EmployeeTds();
        newRow.setTenantId(tenantId);
        newRow.setEmployeeId(employeeId);
        newRow.setFinancialYear(fy.label());
        newRow.setRegime(figures.regime());
        newRow.setSource(source);
        newRow.setDeclarationId(figures.declarationId());
        newRow.setAnnualGross(figures.annualGross().setScale(4, RoundingMode.HALF_UP));
        newRow.setAnnualTaxableIncome(figures.annualTaxableIncome().setScale(4, RoundingMode.HALF_UP));
        newRow.setAnnualTax(figures.annualTax().setScale(4, RoundingMode.HALF_UP));
        newRow.setEffectiveFromPeriod(effectivePeriod);
        newRow.setActive(true);
        newRow.setSupersededAt(null);
        newRow.setNote(figures.note());
        newRow.setCreatedAt(now);
        newRow.setCreatedBy(actor);
        newRow.setUpdatedAt(now);
        newRow.setUpdatedBy(actor);

        EmployeeTds saved = repository.saveAndFlush(newRow);

        BigDecimal ytd = yearToDate(employeeId, fy.label());
        return EmployeeTdsResponse.from(saved, ytd);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTdsResponse> active(UUID employeeId, String financialYear) {
        UUID tenantId = TenantContext.require();
        if (employeeId == null) {
            throw new EmployeeTdsValidationException("employeeId must not be null");
        }
        employeeService.get(employeeId);
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear(financialYear);
        Optional<EmployeeTds> activeRow = repository.findActive(tenantId, employeeId, fy.label());
        if (activeRow.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal ytd = yearToDate(employeeId, fy.label());
        return Optional.of(EmployeeTdsResponse.from(activeRow.get(), ytd));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTds> activeEntity(UUID tenantId, UUID employeeId, String financialYear) {
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear(financialYear);
        return repository.findActive(tenantId, employeeId, fy.label());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeTdsResponse> history(UUID employeeId, String financialYear) {
        UUID tenantId = TenantContext.require();
        if (employeeId == null) {
            throw new EmployeeTdsValidationException("employeeId must not be null");
        }
        employeeService.get(employeeId);
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear(financialYear);
        List<EmployeeTds> rows = repository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc(
                tenantId, employeeId, fy.label());
        BigDecimal ytd = yearToDate(employeeId, fy.label());
        return rows.stream().map(r -> EmployeeTdsResponse.from(r, ytd)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal yearToDate(UUID employeeId, String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear fy = EmployeeTdsRules.validateFinancialYear(financialYear);
        String periodFrom = fy.startYear() + "-04";
        String periodTo = fy.endYear() + "-03";
        BigDecimal ytd = lineRepository.sumTaxLines(tenantId, employeeId, periodFrom, periodTo, null);
        return ytd != null ? ytd.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTdsResponse> activeOwn(String financialYear) {
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new EmployeeTdsNotFoundException("Employee profile not linked to current user"));
        return active(employee.id(), financialYear);
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "system";
    }
}
