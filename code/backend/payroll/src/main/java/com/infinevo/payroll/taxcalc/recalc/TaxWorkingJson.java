package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;

/**
 * Reads a stored tax working ({@code payroll.tax_computation.working}) back for an API response (D-153).
 *
 * <p>Amounts stay exact: decimals are read as {@code BigDecimal}, not {@code double}, and keep the scale
 * they were written at ({@code 1250.5000}, never {@code 1250.5} or {@code 1.25E+3}). A working written
 * before D-153 holds each amount as {@code {"zero":..,"positive":..,"negative":..}}; it is returned as
 * stored, without the amounts it never had. Text that is not JSON is returned as the raw string.
 */
final class TaxWorkingJson {

    private TaxWorkingJson() {}

    static Object read(ObjectMapper mapper, String working) {
        if (working == null) {
            return null;
        }
        try {
            ObjectReader reader = mapper.reader()
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
            return reader.readTree(working);
        } catch (Exception e) {
            return working;
        }
    }
}
