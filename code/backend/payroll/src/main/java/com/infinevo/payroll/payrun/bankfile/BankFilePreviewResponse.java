package com.infinevo.payroll.payrun.bankfile;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a bank file would hold (W-88.2): the headings, the first {@value BankFileExportServiceImpl#PREVIEW_ROWS} rows,
 * the full row count and totals, and who is left out and why.
 */
public record BankFilePreviewResponse(
        String templateName,
        List<String> headings,
        List<List<String>> rows,
        int rowCount,
        BigDecimal fileTotal,
        BigDecimal selectedNetPay,
        BigDecimal runNetPay,
        List<BankFileOmission> omitted) {}
