package com.infinevo.core.billing;

/** Supported payment rails for {@code core.billing_payment} (W-94.1). */
public enum PaymentMethod {
    NEFT,
    RTGS,
    UPI,
    CREDIT_CARD,
    CHEQUE,
    NET_BANKING
}
