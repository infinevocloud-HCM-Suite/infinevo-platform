package com.infinevo.core.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Seller tax configuration for subscription GST invoices (W-94.1).
 *
 * <p>Defaults to Maharashtra ({@code 27}) and SaaS SAC {@code 997331} when no environment override is configured.
 */
@Component
public class BillingProperties {

    public static final String DEFAULT_SELLER_STATE_CODE = "27";
    public static final String DEFAULT_SAC_CODE = "997331";
    public static final int DEFAULT_DUE_DAYS = 15;

    private final String sellerStateCode;
    private final String sacCode;
    private final int defaultDueDays;

    public BillingProperties() {
        this(DEFAULT_SELLER_STATE_CODE, DEFAULT_SAC_CODE, DEFAULT_DUE_DAYS);
    }

    public BillingProperties(
            @Value("${infinevo.billing.seller-state-code:" + DEFAULT_SELLER_STATE_CODE + "}") String sellerStateCode,
            @Value("${infinevo.billing.sac-code:" + DEFAULT_SAC_CODE + "}") String sacCode,
            @Value("${infinevo.billing.default-due-days:" + DEFAULT_DUE_DAYS + "}") int defaultDueDays) {
        if (sellerStateCode == null || !sellerStateCode.trim().matches("^[0-9]{2}$")) {
            throw new IllegalArgumentException("infinevo.billing.seller-state-code must be a 2-digit GST state code");
        }
        if (sacCode == null || sacCode.isBlank()) {
            throw new IllegalArgumentException("infinevo.billing.sac-code must not be blank");
        }
        if (defaultDueDays < 0) {
            throw new IllegalArgumentException("infinevo.billing.default-due-days must not be negative");
        }
        this.sellerStateCode = sellerStateCode.trim();
        this.sacCode = sacCode.trim();
        this.defaultDueDays = defaultDueDays;
    }

    public String sellerStateCode() {
        return sellerStateCode;
    }

    public String sacCode() {
        return sacCode;
    }

    public int defaultDueDays() {
        return defaultDueDays;
    }
}
