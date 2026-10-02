package com.infinevo.payroll.tds;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.tds.exception.EmployeeTdsConflictException;
import com.infinevo.payroll.tds.exception.EmployeeTdsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeTdsService} (W-36.1 §4).
 *
 * <p>Supersedes any existing active record before inserting a new active row. Replaces legacy
 * {@code EmployeePayRunServiceImpl.java:531-640} and {@code DefaultTdsCreationServiceImpl.java:71}.
 */
@Service
public class EmployeeTdsServiceImpl implements EmployeeTdsService {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    private final EmployeeTdsRepository repository;
    private final EmployeePayRunLineRepository lineRepository;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public EmployeeTdsServiceImpl(
            EmployeeTdsRepository repository,
            EmployeePayRunLineRepository lineRepository,
            EmployeeService employeeService,
            @Autowired(required = false) Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.lineRepository = Objects.requireNonNull(lineRepository, "lineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.clock = clock != null ? clock : TaxDeclarationRules.defaultClock();
    }

    public EmployeeTdsServiceImpl(
            EmployeeTdsRepository repository,
            EmployeePayRunLineRepository lineRepository,
            EmployeeService employeeService) {
        this(repository, lineRepository, employeeService, TaxDeclarationRules.defaultClock());
    }

    @Override
    @Transactional
    public EmployeeTds record(UUID employeeId, String financialYear, TdsFigures figures) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(figures, "figures must not be null");

        UUID tenantId = TenantContext.require();
        // Verifies the employee belongs to the bound tenant (throws EmployeeService.NotFoundException -> 404).
        employeeService.get(employeeId);

        FinancialYear fy = validateAndParseFinancialYear(financialYear);
        validateFigures(figures, fy);

        String effectiveFromPeriod = resolveEffectiveFromPeriod(figures.effectiveFromPeriod(), fy);

        // Lock the active row so two concurrent PUTs supersede it one after the other; supersede and
        // flush so the unique partial index is cleared before the insert.
        Optional<EmployeeTds> existingActive = repository.findActiveForUpdate(tenantId, employeeId, fy.label());
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        String actor = currentActor();

        if (existingActive.isPresent()) {
            EmployeeTds active = existingActive.get();
            active.supersede(now, actor);
            repository.saveAndFlush(active);
        }

        EmployeeTds newRecord = new EmployeeTds(
                tenantId,
                employeeId,
                fy.label(),
                figures.regime(),
                figures.source(),
                figures.declarationId(),
                figures.annualGross(),
                figures.annualTaxableIncome(),
                figures.annualTax(),
                effectiveFromPeriod,
                figures.note(),
                actor,
                now);

        try {
            return repository.saveAndFlush(newRecord);
        } catch (DataIntegrityViolationException e) {
            // Two first records for the same year at once: nothing to lock, so the index decides (409).
            throw new EmployeeTdsConflictException(employeeId, fy.label());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTds> active(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();
        FinancialYear fy = validateAndParseFinancialYear(financialYear);
        return repository.findActive(tenantId, employeeId, fy.label());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeTds> history(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();
        FinancialYear fy = validateAndParseFinancialYear(financialYear);
        employeeService.get(employeeId);
        return repository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc(
                tenantId, employeeId, fy.label());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal yearToDate(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();
        FinancialYear fy = validateAndParseFinancialYear(financialYear);
        String periodFrom = String.format("%04d-04", fy.startYear());
        String periodTo = String.format("%04d-03", fy.endYear());
        return lineRepository.sumTaxLines(tenantId, employeeId, periodFrom, periodTo, null);
    }

    private FinancialYear validateAndParseFinancialYear(String financialYear) {
        if (financialYear == null || financialYear.isBlank()) {
            throw new EmployeeTdsValidationException("Financial year must not be null or blank");
        }
        try {
            return FinancialYear.parse(financialYear);
        } catch (IllegalArgumentException e) {
            throw new EmployeeTdsValidationException(e.getMessage());
        }
    }

    private void validateFigures(TdsFigures figures, FinancialYear fy) {
        if (figures.regime() == null) {
            throw new EmployeeTdsValidationException("Tax regime must not be null");
        }
        if (figures.annualTax() == null || figures.annualTax().compareTo(BigDecimal.ZERO) < 0) {
            throw new EmployeeTdsValidationException("Annual tax must be non-negative");
        }
        if (figures.annualGross() == null || figures.annualGross().compareTo(BigDecimal.ZERO) < 0) {
            throw new EmployeeTdsValidationException("Annual gross must be non-negative");
        }
        if (figures.annualTaxableIncome() == null
                || figures.annualTaxableIncome().compareTo(BigDecimal.ZERO) < 0) {
            throw new EmployeeTdsValidationException("Annual taxable income must be non-negative");
        }
        if (figures.annualTaxableIncome().compareTo(figures.annualGross()) > 0) {
            throw new EmployeeTdsValidationException("Annual taxable income cannot exceed annual gross: "
                    + figures.annualTaxableIncome() + " > " + figures.annualGross());
        }
        if (figures.annualTax().scale() > 2) {
            throw new EmployeeTdsValidationException("Annual tax scale must be at most 2");
        }
        if (figures.annualGross().scale() > 2) {
            throw new EmployeeTdsValidationException("Annual gross scale must be at most 2");
        }
        if (figures.annualTaxableIncome().scale() > 2) {
            throw new EmployeeTdsValidationException("Annual taxable income scale must be at most 2");
        }

        if (figures.effectiveFromPeriod() != null
                && !figures.effectiveFromPeriod().isBlank()) {
            String eff = figures.effectiveFromPeriod().trim();
            if (!PERIOD_PATTERN.matcher(eff).matches()) {
                throw new EmployeeTdsValidationException("effective_from_period must match YYYY-MM: " + eff);
            }
            YearMonth effMonth;
            try {
                effMonth = YearMonth.parse(eff);
            } catch (DateTimeParseException e) {
                throw new EmployeeTdsValidationException("Invalid effective_from_period: " + eff);
            }
            YearMonth fyStart = YearMonth.of(fy.startYear(), 4);
            YearMonth fyEnd = YearMonth.of(fy.endYear(), 3);
            if (effMonth.isBefore(fyStart) || effMonth.isAfter(fyEnd)) {
                throw new EmployeeTdsValidationException(
                        "effective_from_period " + eff + " is outside financial year " + fy.label());
            }
        }
    }

    private String resolveEffectiveFromPeriod(String givenPeriod, FinancialYear fy) {
        if (givenPeriod != null && !givenPeriod.isBlank()) {
            return givenPeriod.trim();
        }
        // Payroll months are Indian months, whatever zone the injected clock carries (§4).
        YearMonth currentMonth = YearMonth.now(clock.withZone(TaxDeclarationRules.ZONE));
        YearMonth fyStart = YearMonth.of(fy.startYear(), 4);
        YearMonth fyEnd = YearMonth.of(fy.endYear(), 3);
        if (currentMonth.isBefore(fyStart)) {
            return fyStart.toString();
        } else if (currentMonth.isAfter(fyEnd)) {
            return fyEnd.toString();
        } else {
            return currentMonth.toString();
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
