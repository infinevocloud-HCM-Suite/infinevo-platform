package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registry locator for {@link RegimeCalculator} beans (W-33.1 spec § 2, § 4).
 *
 * <p>Discovers all registered regime calculator beans in the application context. If a calculation
 * is requested for a regime without an available calculator (e.g. {@code OLD} before W-33.2 merges),
 * throws {@link RegimeNotAvailableException} (HTTP 409).
 */
@Component
public class RegimeCalculators {

    private final Map<TaxRegime, RegimeCalculator> calculatorMap = new EnumMap<>(TaxRegime.class);
    private final List<RegimeCalculator> calculators;

    public RegimeCalculators(List<RegimeCalculator> calculators) {
        this.calculators = calculators == null ? List.of() : List.copyOf(calculators);
        for (RegimeCalculator calc : this.calculators) {
            calculatorMap.put(calc.regime(), calc);
        }
    }

    /**
     * Finds the calculator for the requested regime, or throws {@link RegimeNotAvailableException}.
     *
     * @param regime the target regime
     * @return the {@link RegimeCalculator} bean
     */
    public RegimeCalculator forRegime(TaxRegime regime) {
        if (regime == null) {
            throw new RegimeNotAvailableException("null");
        }
        RegimeCalculator calculator = calculatorMap.get(regime);
        if (calculator == null) {
            throw new RegimeNotAvailableException(regime);
        }
        return calculator;
    }

    /**
     * Lists all available/registered regime calculators.
     */
    public List<RegimeCalculator> available() {
        return calculators;
    }
}
