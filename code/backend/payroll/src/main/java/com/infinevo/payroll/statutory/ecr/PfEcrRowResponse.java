package com.infinevo.payroll.statutory.ecr;

/**
 * Single employee record conforming to EPFO ECR 2.0 11-column format (W-87 §4).
 */
public record PfEcrRowResponse(
        String uan,
        String memberName,
        long grossWages,
        long epfWages,
        long epsWages,
        long edliWages,
        long eeShare,
        long epsShare,
        long erShare,
        int ncpDays,
        long refundOfAdvances) {

    public String toEcrLine() {
        return (uan != null ? uan : "") + "#~#"
                + (memberName != null ? memberName : "") + "#~#"
                + grossWages + "#~#"
                + epfWages + "#~#"
                + epsWages + "#~#"
                + edliWages + "#~#"
                + eeShare + "#~#"
                + epsShare + "#~#"
                + erShare + "#~#"
                + ncpDays + "#~#"
                + refundOfAdvances;
    }
}
