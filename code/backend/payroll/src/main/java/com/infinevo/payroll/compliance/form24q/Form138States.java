package com.infinevo.payroll.compliance.form24q;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Protean's numeric state codes for the Form 138 batch header (Annexure 1 of the file format), looked up from a work
 * location's free-text state or its short code. Code 8 does not exist: Daman &amp; Diu merged into 7.
 */
final class Form138States {

    private static final Map<String, Integer> CODES = new HashMap<>();

    static {
        put(1, "ANDAMAN AND NICOBAR ISLANDS", "AN");
        put(2, "ANDHRA PRADESH", "AP");
        put(3, "ARUNACHAL PRADESH", "AR");
        put(4, "ASSAM", "AS");
        put(5, "BIHAR", "BR");
        put(6, "CHANDIGARH", "CH");
        put(7, "DADRA AND NAGAR HAVELI AND DAMAN AND DIU", "DN", "DD", "DH", "DADRA AND NAGAR HAVELI", "DAMAN AND DIU");
        put(9, "DELHI", "DL", "NEW DELHI", "NCT OF DELHI");
        put(10, "GOA", "GA");
        put(11, "GUJARAT", "GJ");
        put(12, "HARYANA", "HR");
        put(13, "HIMACHAL PRADESH", "HP");
        put(14, "JAMMU AND KASHMIR", "JK");
        put(15, "KARNATAKA", "KA");
        put(16, "KERALA", "KL");
        put(17, "LAKSHADWEEP", "LD", "LAKSHWADEEP");
        put(18, "MADHYA PRADESH", "MP");
        put(19, "MAHARASHTRA", "MH");
        put(20, "MANIPUR", "MN");
        put(21, "MEGHALAYA", "ML");
        put(22, "MIZORAM", "MZ");
        put(23, "NAGALAND", "NL");
        put(24, "ODISHA", "OD", "OR", "ORISSA");
        put(25, "PUDUCHERRY", "PY", "PONDICHERRY");
        put(26, "PUNJAB", "PB");
        put(27, "RAJASTHAN", "RJ");
        put(28, "SIKKIM", "SK");
        put(29, "TAMIL NADU", "TN");
        put(30, "TRIPURA", "TR");
        put(31, "UTTAR PRADESH", "UP");
        put(32, "WEST BENGAL", "WB");
        put(33, "CHHATTISGARH", "CG", "CT", "CHHATISGARH");
        put(34, "UTTARAKHAND", "UK", "UT", "UTTARANCHAL");
        put(35, "JHARKHAND", "JH");
        put(36, "TELANGANA", "TS", "TG");
        put(37, "LADAKH", "LA");
    }

    private Form138States() {}

    private static void put(int code, String... names) {
        for (String n : names) {
            CODES.put(n, code);
        }
    }

    /** The code for a state given by name ("Karnataka"), short code ("KA", "IN-KA") or the code itself ("15"). */
    static Optional<Integer> code(String stateCode, String stateName) {
        return lookup(stateCode).or(() -> lookup(stateName));
    }

    private static Optional<Integer> lookup(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace("&", " AND ")
                .replaceAll("^IN-", "")
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (key.matches("\\d{1,2}")) {
            int n = Integer.parseInt(key);
            return CODES.containsValue(n) ? Optional.of(n) : Optional.empty();
        }
        return Optional.ofNullable(CODES.get(key));
    }
}
