package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaxCalculationService} (W-33.1).
 *
 * <p>Coordinates input assembly from CTC versions and previous employment, executes regime
 * calculators, and records summary rows transactionally via {@link TaxSummaryService}.
 */
@Service
@Transactional(readOnly = true)
public class TaxCalculationServiceImpl implements TaxCalculationService {

    private final TaxDeclarationService taxDeclarationService;
    private final TaxInputAssembler taxInputAssembler;
    private final RegimeCalculators regimeCalculators;
    private final TaxSummaryFiguresMapper figuresMapper;
    private final TaxSummaryService taxSummaryService;

    public TaxCalculationServiceImpl(
            TaxDeclarationService taxDeclarationService,
            TaxInputAssembler taxInputAssembler,
            RegimeCalculators regimeCalculators,
            TaxSummaryFiguresMapper figuresMapper,
            TaxSummaryService taxSummaryService) {
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.taxInputAssembler = Objects.requireNonNull(taxInputAssembler, "taxInputAssembler must not be null");
        this.regimeCalculators = Objects.requireNonNull(regimeCalculators, "regimeCalculators must not be null");
        this.figuresMapper = Objects.requireNonNull(figuresMapper, "figuresMapper must not be null");
        this.taxSummaryService = Objects.requireNonNull(taxSummaryService, "taxSummaryService must not be null");
    }

    @Override
    public TaxComputation compute(UUID employeeId, FinancialYear fy, TaxRegime regime) {
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        TaxRegime targetRegime = regime != null ? regime : TaxRegime.from(decl.getTaxRegime());
        TaxInput input = taxInputAssembler.assemble(decl.getTenantId(), employeeId, fy);
        RegimeCalculator calculator = regimeCalculators.forRegime(targetRegime);
        return calculator.compute(input, fy);
    }

    @Override
    @Transactional
    public Map<TaxRegime, TaxComputation> computeAndRecord(UUID employeeId, FinancialYear fy) {
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        TaxInput input = taxInputAssembler.assemble(decl.getTenantId(), employeeId, fy);

        List<RegimeCalculator> available = regimeCalculators.available();
        Map<TaxRegime, TaxComputation> results = new EnumMap<>(TaxRegime.class);

        for (RegimeCalculator calculator : available) {
            TaxComputation computation = calculator.compute(input, fy);
            TaxSummaryFigures figures = figuresMapper.toFigures(computation, fy);
            taxSummaryService.record(decl.getId(), calculator.regime().name(), figures);
            results.put(calculator.regime(), computation);
        }

        return results;
    }
}
