package com.infinevo.core.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Indian GST supply split calculator (W-94.1 §4).
 *
 * <ul>
 *   <li>Intrastate ({@code customerStateCode == sellerStateCode}): CGST 9% + SGST 9% = 18%
 *   <li>Interstate (different state or unknown customer state): IGST 18%
 * </ul>
 */
@Component
public class GstCalculator {

    private static final BigDecimal NINE_PERCENT = new BigDecimal("0.09");
    private static final BigDecimal EIGHTEEN_PERCENT = new BigDecimal("0.18");
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z0-9]{13}$");
    private static final Pattern STATE_CODE_PATTERN = Pattern.compile("^[0-9]{2}$");

    private final BillingProperties properties;

    public GstCalculator(BillingProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public record GstResult(
            String normalizedGstin,
            String customerStateCode,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalTax,
            BigDecimal totalAmount) {}

    public GstResult calculate(BigDecimal taxableAmount, String customerGstin, String customerStateCode) {
        Objects.requireNonNull(taxableAmount, "taxableAmount must not be null");
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("taxableAmount must not be negative");
        }

        String normalizedGstin = normalizeGstin(customerGstin);
        String explicitState = normalizeStateCode(customerStateCode);
        String gstinState = normalizedGstin != null ? normalizedGstin.substring(0, 2) : null;

        if (explicitState != null && gstinState != null && !explicitState.equals(gstinState)) {
            throw new IllegalArgumentException("customerStateCode '" + explicitState
                    + "' does not match customerGstin state '" + gstinState + "'");
        }
        String resolvedCustomerState = gstinState != null ? gstinState : explicitState;
        boolean intrastate =
                resolvedCustomerState != null && resolvedCustomerState.equals(properties.sellerStateCode());

        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgst;
        BigDecimal sgst;
        BigDecimal igst;
        if (intrastate) {
            cgst = taxableAmount.multiply(NINE_PERCENT).setScale(2, RoundingMode.HALF_UP);
            sgst = taxableAmount.multiply(NINE_PERCENT).setScale(2, RoundingMode.HALF_UP);
            igst = zero;
        } else {
            cgst = zero;
            sgst = zero;
            igst = taxableAmount.multiply(EIGHTEEN_PERCENT).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalTax = cgst.add(sgst).add(igst).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = taxableAmount.add(totalTax).setScale(2, RoundingMode.HALF_UP);
        return new GstResult(normalizedGstin, resolvedCustomerState, cgst, sgst, igst, totalTax, totalAmount);
    }

    private static String normalizeGstin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if (!GSTIN_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("customerGstin must be a 15-character alphanumeric GSTIN");
        }
        return trimmed;
    }

    private static String normalizeStateCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!STATE_CODE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("customerStateCode must be a 2-digit GST state code");
        }
        return trimmed;
    }
}
