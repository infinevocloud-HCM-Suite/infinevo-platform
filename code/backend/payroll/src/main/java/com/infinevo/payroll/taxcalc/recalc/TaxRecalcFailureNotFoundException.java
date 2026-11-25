package com.infinevo.payroll.taxcalc.recalc;

import java.util.UUID;

/** No open tax recalculation failure with this id in the bound tenant (D-147); answered with 404. */
public class TaxRecalcFailureNotFoundException extends RuntimeException {

    public TaxRecalcFailureNotFoundException(UUID id) {
        super("No open tax recalculation failure " + id);
    }
}
