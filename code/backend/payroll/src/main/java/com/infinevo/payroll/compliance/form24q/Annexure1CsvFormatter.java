package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Formats Form 24Q Annexure I dataset into RFC 4180 compliant CSV.
 */
public final class Annexure1CsvFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private Annexure1CsvFormatter() {}

    public static byte[] format(Annexure1PreviewResponse preview) {
        StringBuilder sb = new StringBuilder();

        // CSV Header
        sb.append("Challan Serial No,BSR Code,Challan Date,Minor Head,Section,Deductee Code,PAN of Deductee,Name of"
                        + " Deductee,Date of Payment,Amount Paid,Tax Deducted,Surcharge,Cess,Total Tax Deducted,Total"
                        + " Tax Deposited,Date of Deduction,Remarks")
                .append("\r\n");

        Map<UUID, ChallanDetailRecord> challanMap = preview.challans().stream()
                .filter(c -> c.challanId() != null)
                .collect(Collectors.toMap(ChallanDetailRecord::challanId, c -> c, (a, b) -> a));

        for (DeducteeDetailRecord d : preview.deductees()) {
            ChallanDetailRecord c = d.matchedChallanId() != null ? challanMap.get(d.matchedChallanId()) : null;

            String challanSerial = c != null ? c.challanSerialNo() : "";
            String bsrCode = c != null ? c.bsrCode() : "";
            String challanDate =
                    c != null && c.tenderDate() != null ? c.tenderDate().format(DATE_FORMATTER) : "";
            String minorHead = c != null && c.minorHead() != null ? c.minorHead() : "200";

            sb.append(escape(challanSerial)).append(",");
            sb.append(escape(bsrCode)).append(",");
            sb.append(escape(challanDate)).append(",");
            sb.append(escape(minorHead)).append(",");
            sb.append(escape(d.sectionCode())).append(",");
            sb.append(escape(d.deducteeCode())).append(",");
            sb.append(escape(d.pan())).append(",");
            sb.append(escape(d.employeeName())).append(",");
            sb.append(escape(d.paymentDate() != null ? d.paymentDate().format(DATE_FORMATTER) : ""))
                    .append(",");
            sb.append(formatAmount(d.grossAmount())).append(",");
            sb.append(formatAmount(d.tdsAmount())).append(",");
            sb.append(formatAmount(d.surchargeAmount())).append(",");
            sb.append(formatAmount(d.cessAmount())).append(",");
            sb.append(formatAmount(d.totalTaxDeducted())).append(",");
            sb.append(formatAmount(d.totalTaxDeposited())).append(",");
            sb.append(escape(d.deductionDate() != null ? d.deductionDate().format(DATE_FORMATTER) : ""))
                    .append(",");
            sb.append(escape(d.reasonForNonDeduction() != null ? d.reasonForNonDeduction() : ""));
            sb.append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return "\"" + value + "\"";
    }
}
