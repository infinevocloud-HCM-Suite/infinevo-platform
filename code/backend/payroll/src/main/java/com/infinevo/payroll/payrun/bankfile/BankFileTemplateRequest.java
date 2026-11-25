package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.report.ExportFormat;
import java.util.List;

/** Create or replace a bank file template (W-88.2 spec section 4.4). */
public record BankFileTemplateRequest(
        String name, ExportFormat fileFormat, Boolean includeHeader, List<BankFileColumn> columns) {}
