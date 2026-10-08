package com.infinevo.payroll.taxcalc;

import java.util.Locale;
import java.util.Set;

/**
 * Recognises the earnings the tax rules key on by component code or earning type (D-37).
 *
 * <p>Earning types are the named list the drawer offers ("Basic", "House Rent Allowance", ...), so a
 * component is HRA when its code is {@code HRA} or its type spells the allowance out.
 */
public final class EarningKinds {

    private static final Set<String> HRA = Set.of("HRA", "HOUSE RENT ALLOWANCE");
    private static final Set<String> BASIC = Set.of("BASIC", "BASIC SALARY");

    public static boolean isHra(String code, String earningType) {
        return matches(HRA, code) || matches(HRA, earningType);
    }

    public static boolean isBasic(String code, String earningType) {
        return matches(BASIC, code) || matches(BASIC, earningType);
    }

    private static boolean matches(Set<String> names, String value) {
        return value != null && names.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private EarningKinds() {}
}
