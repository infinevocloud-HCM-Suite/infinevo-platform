package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Formats Form 24Q Annexure I dataset into NSDL e-TDS FVU structured text format.
 */
public final class NsdlFvuTextFormatter {

    private static final DateTimeFormatter NSDL_DATE = DateTimeFormatter.ofPattern("ddMMyyyy");

    private NsdlFvuTextFormatter() {}

    public static byte[] format(Annexure1PreviewResponse preview) {
        StringBuilder sb = new StringBuilder();
        int lineNo = 1;

        // FH: File Header
        sb.append("FH^").append(lineNo++).append("^NSDL-24Q^R^1.0^ASCII^^^^^^^^^^^\r\n");

        int batchNo = 1;
        int challanCount = Math.max(1, preview.challans().size());

        // Clean FY and AY for NSDL (e.g., 2026-2027 -> 202627)
        String fyCode = toNsdlYear(preview.financialYear());
        String ayCode = toNsdlYear(preview.assessmentYear());

        // BH: Batch Header
        sb.append("BH^")
                .append(lineNo++)
                .append("^")
                .append(batchNo)
                .append("^24Q^^^")
                .append(preview.tan() != null ? preview.tan() : "")
                .append("^")
                .append(preview.pan() != null ? preview.pan() : "")
                .append("^")
                .append(fyCode)
                .append("^")
                .append(ayCode)
                .append("^")
                .append(preview.quarter())
                .append("^")
                .append(preview.companyName() != null ? preview.companyName() : "")
                .append("^^^^^")
                .append(formatAmount(preview.totalGrossPaid()))
                .append("^")
                .append(formatAmount(preview.totalTdsDeducted()))
                .append("^")
                .append(formatAmount(preview.totalChallanAmount()))
                .append("^\r\n");

        // Group deductees by matched challan ID
        Map<UUID, List<DeducteeDetailRecord>> deducteesByChallan = preview.deductees().stream()
                .filter(d -> d.matchedChallanId() != null)
                .collect(Collectors.groupingBy(DeducteeDetailRecord::matchedChallanId));

        List<DeducteeDetailRecord> unallocatedDeductees = preview.deductees().stream()
                .filter(d -> d.matchedChallanId() == null)
                .toList();

        List<ChallanDetailRecord> challanList = preview.challans();
        if (challanList.isEmpty()) {
            // Emitting single fallback CD record with unallocated deductees
            sb.append("CD^")
                    .append(lineNo++)
                    .append("^")
                    .append(batchNo)
                    .append("^1^")
                    .append(unallocatedDeductees.size())
                    .append("^200^^^^^^^^0.00^0.00^0.00^0.00^0.00^0.00^\r\n");

            int ddIndex = 1;
            for (DeducteeDetailRecord d : unallocatedDeductees) {
                appendDeductee(sb, lineNo++, batchNo, 1, ddIndex++, d);
            }
        } else {
            int challanSerial = 1;
            for (ChallanDetailRecord c : challanList) {
                List<DeducteeDetailRecord> cdDeductees =
                        new ArrayList<>(deducteesByChallan.getOrDefault(c.challanId(), Collections.emptyList()));

                // If first challan, also attach unallocated deductees if any
                if (challanSerial == 1 && !unallocatedDeductees.isEmpty()) {
                    cdDeductees.addAll(unallocatedDeductees);
                }

                sb.append("CD^")
                        .append(lineNo++)
                        .append("^")
                        .append(batchNo)
                        .append("^")
                        .append(challanSerial)
                        .append("^")
                        .append(cdDeductees.size())
                        .append("^")
                        .append(c.minorHead() != null ? c.minorHead() : "200")
                        .append("^^")
                        .append(c.bsrCode() != null ? c.bsrCode() : "")
                        .append("^")
                        .append(c.tenderDate() != null ? c.tenderDate().format(NSDL_DATE) : "")
                        .append("^")
                        .append(c.challanSerialNo() != null ? c.challanSerialNo() : "")
                        .append("^^")
                        .append(formatAmount(c.tax()))
                        .append("^")
                        .append(formatAmount(c.surcharge()))
                        .append("^")
                        .append(formatAmount(c.cess()))
                        .append("^")
                        .append(formatAmount(c.interest()))
                        .append("^")
                        .append(formatAmount(c.fee()))
                        .append("^")
                        .append(formatAmount(c.totalAmount()))
                        .append("^\r\n");

                int ddIndex = 1;
                for (DeducteeDetailRecord d : cdDeductees) {
                    appendDeductee(sb, lineNo++, batchNo, challanSerial, ddIndex++, d);
                }

                challanSerial++;
            }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendDeductee(
            StringBuilder sb, int lineNo, int batchNo, int challanSerial, int ddIndex, DeducteeDetailRecord d) {
        sb.append("DD^")
                .append(lineNo)
                .append("^")
                .append(batchNo)
                .append("^")
                .append(challanSerial)
                .append("^")
                .append(ddIndex)
                .append("^01^")
                .append(d.deducteeCode() != null ? d.deducteeCode() : "02")
                .append("^")
                .append(d.pan() != null ? d.pan() : "PANNOTAVBL")
                .append("^")
                .append(d.employeeName() != null ? d.employeeName() : "")
                .append("^")
                .append(formatDate(d.paymentDate()))
                .append("^")
                .append(formatAmount(d.grossAmount()))
                .append("^")
                .append(formatAmount(d.tdsAmount()))
                .append("^")
                .append(formatAmount(d.surchargeAmount()))
                .append("^")
                .append(formatAmount(d.cessAmount()))
                .append("^")
                .append(formatAmount(d.totalTaxDeducted()))
                .append("^")
                .append(formatAmount(d.totalTaxDeposited()))
                .append("^")
                .append(formatDate(d.deductionDate()))
                .append("^")
                .append(d.reasonForNonDeduction() != null ? d.reasonForNonDeduction() : "")
                .append("^")
                .append(d.certificateNumber() != null ? d.certificateNumber() : "")
                .append("^\r\n");
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(NSDL_DATE) : "";
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String toNsdlYear(String fyOrAy) {
        if (fyOrAy == null) {
            return "";
        }
        // e.g. "2026-2027" -> "202627"
        String[] parts = fyOrAy.split("-");
        if (parts.length == 2 && parts[0].length() == 4 && parts[1].length() == 4) {
            return parts[0] + parts[1].substring(2);
        }
        return fyOrAy.replace("-", "");
    }

    public static byte[] formatAnnexure2(Annexure2PreviewResponse preview) {
        StringBuilder sb = new StringBuilder();
        int lineNo = 1;

        // FH: File Header
        sb.append("FH^").append(lineNo++).append("^NSDL-24Q^R^1.0^ASCII^^^^^^^^^^^\r\n");

        int batchNo = 1;
        String fyCode = toNsdlYear(preview.financialYear());
        String ayCode = toNsdlYear(preview.assessmentYear());

        // BH: Batch Header
        sb.append("BH^")
                .append(lineNo++)
                .append("^")
                .append(batchNo)
                .append("^24Q^^^")
                .append(preview.tan() != null ? preview.tan() : "")
                .append("^")
                .append(preview.pan() != null ? preview.pan() : "")
                .append("^")
                .append(fyCode)
                .append("^")
                .append(ayCode)
                .append("^Q4^")
                .append(preview.companyName() != null ? preview.companyName() : "")
                .append("^^^^^")
                .append(formatAmount(preview.totalGrossSalary()))
                .append("^")
                .append(formatAmount(preview.totalTdsDeducted()))
                .append("^")
                .append(formatAmount(preview.totalTaxPayable()))
                .append("^\r\n");

        int sdIndex = 1;
        for (SalaryDetailRecord r : preview.records()) {
            sb.append("SD^")
                    .append(lineNo++)
                    .append("^")
                    .append(batchNo)
                    .append("^")
                    .append(sdIndex++)
                    .append("^02^")
                    .append(r.pan() != null ? r.pan() : "PANNOTAVBL")
                    .append("^")
                    .append(r.employeeName() != null ? r.employeeName() : "")
                    .append("^")
                    .append("NEW".equalsIgnoreCase(r.regime()) ? "Y" : "N")
                    .append("^")
                    .append(formatDate(r.employmentFrom()))
                    .append("^")
                    .append(formatDate(r.employmentTo()))
                    .append("^")
                    .append(formatAmount(r.grossSalary()))
                    .append("^")
                    .append(formatAmount(r.exemptionsSection10()))
                    .append("^")
                    .append(formatAmount(r.standardDeduction()))
                    .append("^")
                    .append(formatAmount(r.professionalTax()))
                    .append("^")
                    .append(formatAmount(r.totalDeductionsSection16()))
                    .append("^")
                    .append(formatAmount(r.incomeChargeableUnderSalaries()))
                    .append("^")
                    .append(formatAmount(r.housePropertyIncome()))
                    .append("^")
                    .append(formatAmount(r.otherIncome()))
                    .append("^")
                    .append(formatAmount(r.grossTotalIncome()))
                    .append("^")
                    .append(formatAmount(r.chapterViaDeductions()))
                    .append("^")
                    .append(formatAmount(r.totalTaxableIncome()))
                    .append("^")
                    .append(formatAmount(r.taxOnTotalIncome()))
                    .append("^")
                    .append(formatAmount(r.rebate87A()))
                    .append("^")
                    .append(formatAmount(r.surcharge()))
                    .append("^")
                    .append(formatAmount(r.cess()))
                    .append("^")
                    .append(formatAmount(r.relief89()))
                    .append("^")
                    .append(formatAmount(r.totalTaxPayable()))
                    .append("^")
                    .append(formatAmount(r.totalTdsDeducted()))
                    .append("^")
                    .append(formatAmount(r.prevEmployerTds()))
                    .append("^\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
