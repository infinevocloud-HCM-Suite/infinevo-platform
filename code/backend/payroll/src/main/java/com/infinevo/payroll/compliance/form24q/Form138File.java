package com.infinevo.payroll.compliance.form24q;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Form 138 quarterly TDS statement on salary, Q1 to Q3, as the text file Protean's FVU 1.2 validates (W-89;
 * layout "Form Number 138-24Q - Q1 to Q3", tax year 2026-27 onwards). FH, BH, then each challan (CD) followed by
 * its employees (DD). Fields are separated by {@code ^}, every field present (FH 18, BH 72, CD 30, DD 45), empty
 * ones as nothing, lines end CR LF, ASCII only, amounts with two decimals, dates ddMMyyyy.
 */
final class Form138File {

    static final String RPU_NAME = "Infinevo HCM";
    static final String SECTION_PRIVATE_EMPLOYER = "1002";
    static final int FH_FIELDS = 18;
    static final int BH_FIELDS = 72;
    static final int CD_FIELDS = 30;
    static final int DD_FIELDS = 45;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private Form138File() {}

    /**
     * @param previousToken the token of this year's previous regular Form 138 statement, or null when this is the
     *     first (Q1, or nothing filed yet)
     */
    static byte[] write(Annexure1PreviewResponse p, Form138Deductor d, String previousToken, LocalDate created) {
        List<String[]> records = new ArrayList<>();
        int[] line = {0};

        String[] fh = record(FH_FIELDS, ++line[0], "FH");
        fh[2] = "SL1";
        fh[3] = "R";
        fh[4] = DATE.format(created);
        fh[5] = "1";
        fh[6] = "D";
        fh[7] = d.tan();
        fh[8] = "1";
        fh[9] = RPU_NAME;
        records.add(fh);

        String[] bh = record(BH_FIELDS, ++line[0], "BH");
        bh[2] = "1";
        bh[3] = String.valueOf(p.challans().size());
        bh[4] = "138";
        bh[8] = previousToken == null ? "" : previousToken;
        bh[12] = d.tan();
        bh[14] = d.pan();
        int start = Integer.parseInt(p.financialYear().substring(0, 4));
        bh[15] = (start + 1) + twoDigits(start + 2);
        bh[16] = start + twoDigits(start + 1);
        bh[17] = p.quarter();
        bh[18] = text(d.name(), 75);
        bh[19] = "INDIA";
        List<String> address = d.addressLines();
        // Address 1 is mandatory; 2 to 5 optional. The lines fill 1, 2, 3, 4, 5 in order.
        for (int i = 0; i < 5; i++) {
            bh[20 + i] = i < address.size() ? address.get(i) : "";
        }
        bh[25] = String.valueOf(d.stateCode());
        bh[26] = d.pin();
        bh[27] = d.email();
        bh[28] = "91";
        bh[29] = d.phone();
        bh[31] = d.deductorType();
        bh[32] = text(d.responsibleName(), 75);
        bh[33] = text(d.responsibleDesignation(), 20);
        for (int i = 0; i < 5; i++) {
            bh[34 + i] = i < address.size() ? address.get(i) : "";
        }
        bh[39] = String.valueOf(d.stateCode());
        bh[40] = d.pin();
        bh[41] = d.responsibleEmail();
        bh[42] = "INDIA";
        bh[43] = "91";
        bh[44] = d.responsiblePhone();
        bh[46] = whole(
                p.challans().stream().map(ChallanDetailRecord::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        bh[51] = previousToken == null ? "N" : "Y";
        bh[58] = d.responsiblePan();
        bh[68] = d.gstin() == null ? "" : d.gstin();
        records.add(bh);

        Map<UUID, List<DeducteeDetailRecord>> byChallan = p.deductees().stream()
                .filter(r -> r.matchedChallanId() != null)
                .collect(Collectors.groupingBy(DeducteeDetailRecord::matchedChallanId));

        for (ChallanDetailRecord c : p.challans()) {
            List<DeducteeDetailRecord> rows = byChallan.getOrDefault(c.challanId(), List.of());
            BigDecimal deposited =
                    rows.stream().map(DeducteeDetailRecord::totalTaxDeposited).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deducted =
                    rows.stream().map(DeducteeDetailRecord::totalTaxDeducted).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = c.tax().add(c.surcharge()).add(c.cess());

            String[] cd = record(CD_FIELDS, ++line[0], "CD");
            cd[2] = "1";
            cd[3] = String.valueOf(c.serialNumberInBatch());
            cd[4] = String.valueOf(rows.size());
            cd[5] = "N";
            cd[7] = whole(tax);
            cd[8] = whole(c.interest());
            cd[9] = whole(c.fee());
            cd[10] = whole(BigDecimal.ZERO);
            cd[11] = whole(c.totalAmount());
            cd[12] = "C";
            cd[14] = c.bsrCode();
            cd[16] = String.valueOf(Integer.parseInt(c.challanSerialNo()));
            cd[18] = DATE.format(c.tenderDate());
            cd[20] = money(deposited);
            cd[21] = money(deducted);
            cd[22] = c.minorHead();
            cd[23] = cd[8];
            cd[24] = cd[10];
            records.add(cd);

            int serial = 0;
            for (DeducteeDetailRecord r : rows) {
                String[] dd = record(DD_FIELDS, ++line[0], "DD");
                dd[2] = "1";
                dd[3] = String.valueOf(++serial);
                dd[4] = String.valueOf(c.serialNumberInBatch());
                dd[5] = "O";
                dd[7] = r.pan();
                dd[8] = text(r.employeeName(), 75);
                dd[14] = SECTION_PRIVATE_EMPLOYER;
                dd[17] = "Y";
                dd[18] = DATE.format(r.paymentDate());
                dd[19] = money(r.grossAmount());
                dd[23] = money(r.totalTaxDeducted());
                dd[24] = money(r.totalTaxDeposited());
                dd[26] = r.totalTaxDeducted().signum() > 0 ? DATE.format(r.deductionDate()) : "";
                dd[28] = r.totalTaxDeposited().signum() > 0 ? DATE.format(c.tenderDate()) : "";
                dd[31] = r.reasonForNonDeduction() == null ? "" : r.reasonForNonDeduction();
                records.add(dd);
            }
        }

        StringBuilder out = new StringBuilder();
        for (String[] r : records) {
            out.append(String.join("^", r)).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String[] record(int fields, int line, String type) {
        String[] r = new String[fields];
        java.util.Arrays.fill(r, "");
        r[0] = String.valueOf(line);
        r[1] = type;
        return r;
    }

    private static String twoDigits(int year) {
        return String.format(Locale.ROOT, "%02d", year % 100);
    }

    /** Two decimals, e.g. 1234.50. */
    static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** A challan amount: whole rupees written with .00, as the CD record requires. */
    static String whole(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        if (x.stripTrailingZeros().scale() > 0) {
            throw new IllegalStateException("Challan amounts must be whole rupees, was " + x.toPlainString());
        }
        return x.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** Plain ASCII, no {@code ^}, single spaces, at most {@code max} characters. */
    static String text(String value, int max) {
        String s = ascii(value).trim();
        return s.length() > max ? s.substring(0, max).trim() : s;
    }

    static String ascii(String value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^\\x20-\\x7E]", " ").replace('^', ' ').replaceAll("\\s+", " ");
    }
}
