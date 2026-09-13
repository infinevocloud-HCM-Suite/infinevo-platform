package com.itsdev.payroll.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ProfessionalTaxUtil {

    private static final Set<String> PT_SUPPORTED_STATES = Set.of(
            "Andhra Pradesh","Assam","Bihar","Chhattisgarh","Gujarat",
            "Jharkhand","Karnataka","Kerala","Madhya Pradesh","Maharashtra",
            "Manipur","Mizoram","Nagaland","Odisha","Punjab","Puducherry",
            "Sikkim","Tamil Nadu","Telangana","Tripura","West Bengal"
    );

    public static boolean isProfessionalTaxSupported(String state) {
        return PT_SUPPORTED_STATES.contains(state);
    }
}
