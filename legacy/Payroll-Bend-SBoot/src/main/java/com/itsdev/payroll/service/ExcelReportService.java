package com.itsdev.payroll.service;

import com.itsdev.payroll.entity.payruns.EmployeePayRun;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.payruns.EmployeePayRunRepository;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
public class ExcelReportService {

    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final OrganizationRepository organizationRepository;

    public ExcelReportService(PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            OrganizationRepository organizationRepository) {
        this.payRunRepository = payRunRepository;
        this.employeePayRunRepository = employeePayRunRepository;
        this.organizationRepository = organizationRepository;
    }

    private static final Logger log = LoggerFactory.getLogger(ExcelReportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    // ========== CURRENCY CELL METHODS (KEEP ONLY ONE SET) ==========
    /**
     * Create a currency cell with Double value
     */
    private void createCurrencyCell(Row row, int col, Double value, CellStyle currencyStyle) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(currencyStyle);
    }

    /**
     * Create a currency cell with BigDecimal value
     */
    private void createCurrencyCell(Row row, int col, BigDecimal value, CellStyle currencyStyle) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(currencyStyle);
    }

    /**
     * Create a currency cell with double primitive
     */
    private void createCurrencyCell(Row row, int col, double value, CellStyle currencyStyle) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(currencyStyle);
    }

    // ========== STYLE METHODS ==========
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("dd-mmm-yyyy"));
        return style;
    }

    private CellStyle createSubHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    // ========== MAIN REPORT METHODS ==========
    /**
     * Generate consolidated Excel report for ALL completed pay runs of an
     * organization
     */
    public byte[] generateAllPayRunsReport(String organizationId, LocalDate fromDate, LocalDate toDate)
            throws IOException {
        String method = "generateAllPayRunsReport";
        log.info("[{}] 📊 Generating consolidated Excel report for organizationId={} from {} to {}",
                method, organizationId, fromDate, toDate);

        // FIX: You need to fetch organization properly
        // Fetch organization properly
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        List<PayRun> allPayRuns;

        // Fetch completed pay runs and filter by date
        List<PayRun> completedPayRuns = payRunRepository.findByOrganizationAndStatus(org, PayRunStatus.COMPLETED);

        if (fromDate != null && toDate != null) {
            allPayRuns = completedPayRuns.stream()
                    .filter(pr -> pr.getPayDate() != null
                            && !pr.getPayDate().isBefore(fromDate)
                            && !pr.getPayDate().isAfter(toDate))
                    .collect(Collectors.toList());
        } else {
            allPayRuns = completedPayRuns;
        }

        log.info("[{}] ✅ Found {} completed pay runs for organization", method, allPayRuns.size());

        Map<PayRun, List<EmployeePayRun>> payRunData = new LinkedHashMap<>();
        List<EmployeePayRun> allEmployeePayRuns = new ArrayList<>();

        for (PayRun payRun : allPayRuns) {
            List<EmployeePayRun> employeePayRuns = employeePayRunRepository.findByPayrunId(payRun.getPayrunId());
            payRunData.put(payRun, employeePayRuns);
            allEmployeePayRuns.addAll(employeePayRuns);

            log.info("[{}] 📋 PayRun {} has {} employees",
                    method, payRun.getProcessingPeriod(), employeePayRuns.size());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            Sheet summarySheet = workbook.createSheet("Pay Run Summary");
            createConsolidatedSummarySheet(summarySheet, allPayRuns, headerStyle, currencyStyle, dateStyle);

            Sheet employeeSheet = workbook.createSheet("Employee Details");
            createAllEmployeesSheet(employeeSheet, allEmployeePayRuns, headerStyle, currencyStyle);

            Sheet payRunWiseSheet = workbook.createSheet("Pay Run Wise Summary");
            createPayRunWiseSheet(payRunWiseSheet, payRunData, headerStyle, currencyStyle, dateStyle);

            autoSizeAllSheets(workbook);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("[{}] ✅ Consolidated Excel report generated successfully for {} pay runs",
                    method, allPayRuns.size());

            return outputStream.toByteArray();
        }
    }

    // ========== NEW SHEET METHODS (ADD THESE) ==========
    /**
     * Sheet 1: Consolidated Summary (Like Dashboard)
     */
    private void createConsolidatedSummarySheet(Sheet sheet, List<PayRun> payRuns,
            CellStyle headerStyle, CellStyle currencyStyle, CellStyle dateStyle) {

        // Title Row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("PAY RUN CONSOLIDATED REPORT");
        CellStyle titleStyle = sheet.getWorkbook().createCellStyle();
        Font titleFont = sheet.getWorkbook().createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // Summary Header
        Row headerRow = sheet.createRow(2);
        String[] headers = { "Metric", "Value", "Remarks" };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Calculate totals
        BigDecimal totalNetPay = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        BigDecimal totalEPF = BigDecimal.ZERO;
        BigDecimal totalESI = BigDecimal.ZERO;
        int totalEmployees = 0;

        for (PayRun payRun : payRuns) {
            totalNetPay = totalNetPay.add(payRun.getTotalNetPay() != null ? payRun.getTotalNetPay() : BigDecimal.ZERO);
            totalTaxes = totalTaxes.add(payRun.getTotalTaxes() != null ? payRun.getTotalTaxes() : BigDecimal.ZERO);
            totalEPF = totalEPF
                    .add(payRun.getTotalEpfContribution() != null ? payRun.getTotalEpfContribution() : BigDecimal.ZERO);
            totalESI = totalESI
                    .add(payRun.getTotalEsiContribution() != null ? payRun.getTotalEsiContribution() : BigDecimal.ZERO);
            totalEmployees += payRun.getNoOfEmployees() != null ? payRun.getNoOfEmployees() : 0;
        }

        int rowNum = 3;
        addSummaryRow(sheet, rowNum++, "Total Pay Runs", String.valueOf(payRuns.size()), "");
        addSummaryRow(sheet, rowNum++, "Total Employees Processed", String.valueOf(totalEmployees), "");
        addSummaryCurrencyRow(sheet, rowNum++, "Total Net Pay", totalNetPay, currencyStyle, "Sum of all pay runs");
        addSummaryCurrencyRow(sheet, rowNum++, "Total Taxes", totalTaxes, currencyStyle,
                "Income tax + Professional tax");
        addSummaryCurrencyRow(sheet, rowNum++, "Total EPF Contribution", totalEPF, currencyStyle,
                "Employer + Employee");
        addSummaryCurrencyRow(sheet, rowNum++, "Total ESI Contribution", totalESI, currencyStyle,
                "Employer + Employee");

        // Date Range
        if (!payRuns.isEmpty()) {
            LocalDate earliestDate = payRuns.stream()
                    .map(PayRun::getPayDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            LocalDate latestDate = payRuns.stream()
                    .map(PayRun::getPayDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            if (earliestDate != null && latestDate != null) {
                addSummaryRow(sheet, rowNum++, "Report Period",
                        earliestDate.format(MONTH_FORMATTER) + " to " + latestDate.format(MONTH_FORMATTER),
                        "Based on pay dates");
            }
        }

        // Empty row
        rowNum++;

        // Pay Run List Header
        Row listHeaderRow = sheet.createRow(rowNum++);
        String[] listHeaders = { "Pay Run ID", "Processing Period", "Pay Date", "Status",
                "Employees", "Net Pay", "Taxes", "EPF", "ESI" };

        for (int i = 0; i < listHeaders.length; i++) {
            Cell cell = listHeaderRow.createCell(i);
            cell.setCellValue(listHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        // Pay Run List Data
        for (PayRun payRun : payRuns) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;

            row.createCell(col++).setCellValue(payRun.getPayrunId());
            row.createCell(col++)
                    .setCellValue(payRun.getProcessingPeriod() != null ? payRun.getProcessingPeriod() : "");

            if (payRun.getPayDate() != null) {
                Cell dateCell = row.createCell(col++);
                dateCell.setCellValue(payRun.getPayDate().format(DATE_FORMATTER));
                dateCell.setCellStyle(dateStyle);
            } else {
                row.createCell(col++).setCellValue("");
            }

            row.createCell(col++).setCellValue(payRun.getStatus() != null ? payRun.getStatus().name() : "");
            row.createCell(col++).setCellValue(payRun.getNoOfEmployees() != null ? payRun.getNoOfEmployees() : 0);

            createCurrencyCell(row, col++,
                    payRun.getTotalNetPay() != null ? payRun.getTotalNetPay().doubleValue() : 0.0, currencyStyle);
            createCurrencyCell(row, col++, payRun.getTotalTaxes() != null ? payRun.getTotalTaxes().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEpfContribution() != null ? payRun.getTotalEpfContribution().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEsiContribution() != null ? payRun.getTotalEsiContribution().doubleValue() : 0.0,
                    currencyStyle);
        }
    }

    /**
     * Sheet 2: All Employee Details
     */
    private void createAllEmployeesSheet(Sheet sheet, List<EmployeePayRun> allEmployeePayRuns,
            CellStyle headerStyle, CellStyle currencyStyle) {

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Pay Run ID", "Processing Period",
                "Employee Number", "Employee Name",
                "Payment Status", "Payment Mode",
                "Total Earnings", "Total Deductions", "Total Taxes",
                "Total Benefits", "Total Reimbursements",
                "Net Pay", "Monthly Salary", "Paid Days",
                "Leaves Taken (Current Pay Period)", "LOP Amount", "Monthly TDS",
                "Bonus Eligible", "Tax Overridden", "Employee Status"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add employee data rows
        int rowNum = 1;
        for (EmployeePayRun empPayRun : allEmployeePayRuns) {
            Row row = sheet.createRow(rowNum++);

            int col = 0;

            // Pay Run Info
            row.createCell(col++).setCellValue(empPayRun.getPayrunId() != null ? empPayRun.getPayrunId() : "");
            if (empPayRun.getPayRun() != null && empPayRun.getPayRun().getProcessingPeriod() != null) {
                row.createCell(col++).setCellValue(empPayRun.getPayRun().getProcessingPeriod());
            } else {
                row.createCell(col++).setCellValue("");
            }

            // Employee Info
            // row.createCell(col++).setCellValue(empPayRun.getEmployeeId() != null ?
            // empPayRun.getEmployeeId() : "");
            row.createCell(col++)
                    .setCellValue(empPayRun.getEmployeeNumber() != null ? empPayRun.getEmployeeNumber() : "");
            row.createCell(col++).setCellValue(empPayRun.getEmployeeName() != null ? empPayRun.getEmployeeName() : "");
            row.createCell(col++)
                    .setCellValue(empPayRun.getPaymentStatus() != null ? empPayRun.getPaymentStatus() : "");
            row.createCell(col++).setCellValue(empPayRun.getPaymentMode() != null ? empPayRun.getPaymentMode() : "");

            createCurrencyCell(row, col++, empPayRun.getTotalEarnings(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalDeductions(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalTaxes(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalBenefits(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalReimbursements(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getNetPay(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getMonthlySalary(), currencyStyle);

            row.createCell(col++).setCellValue(empPayRun.getPaidDays() != null ? empPayRun.getPaidDays() : 0.0);
            row.createCell(col++)
                    .setCellValue(empPayRun.getTotalNoOfLeaves() != null ? empPayRun.getTotalNoOfLeaves() : 0.0);
            createCurrencyCell(row, col++, empPayRun.getLOP(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getMonthlyTds(), currencyStyle);

            row.createCell(col++)
                    .setCellValue(empPayRun.getBonusEarningExistForEmployee() != null
                            ? empPayRun.getBonusEarningExistForEmployee()
                            : false);
            row.createCell(col++)
                    .setCellValue(empPayRun.getTaxOverridden() != null ? empPayRun.getTaxOverridden() : false);
            row.createCell(col++)
                    .setCellValue(empPayRun.getEmployeeStatus() != null ? empPayRun.getEmployeeStatus() : "");
        }

        // Add totals row
        addEmployeeTotalsRow(sheet, rowNum, allEmployeePayRuns, currencyStyle);
    }

    /**
     * Sheet 3: Pay Run Wise Summary
     */
    private void createPayRunWiseSheet(Sheet sheet, Map<PayRun, List<EmployeePayRun>> payRunData,
            CellStyle headerStyle, CellStyle currencyStyle, CellStyle dateStyle) {

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Pay Run ID", "Processing Period", "Pay Date", "Status",
                "Total Employees", "Total Net Pay", "Total Taxes", "Total Benefits",
                "Total Deductions", "EPF Contribution", "ESI Contribution",
                "EDLI Contribution", "Admin Charges"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add pay run data rows
        int rowNum = 1;
        for (Map.Entry<PayRun, List<EmployeePayRun>> entry : payRunData.entrySet()) {
            PayRun payRun = entry.getKey();

            Row row = sheet.createRow(rowNum++);
            int col = 0;

            row.createCell(col++).setCellValue(payRun.getPayrunId());
            row.createCell(col++)
                    .setCellValue(payRun.getProcessingPeriod() != null ? payRun.getProcessingPeriod() : "");

            if (payRun.getPayDate() != null) {
                Cell dateCell = row.createCell(col++);
                dateCell.setCellValue(payRun.getPayDate().format(DATE_FORMATTER));
                dateCell.setCellStyle(dateStyle);
            } else {
                row.createCell(col++).setCellValue("");
            }

            row.createCell(col++).setCellValue(payRun.getStatus() != null ? payRun.getStatus().name() : "");
            row.createCell(col++).setCellValue(payRun.getNoOfEmployees() != null ? payRun.getNoOfEmployees() : 0);

            createCurrencyCell(row, col++,
                    payRun.getTotalNetPay() != null ? payRun.getTotalNetPay().doubleValue() : 0.0, currencyStyle);
            createCurrencyCell(row, col++, payRun.getTotalTaxes() != null ? payRun.getTotalTaxes().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalBenefits() != null ? payRun.getTotalBenefits().doubleValue() : 0.0, currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalDeductions() != null ? payRun.getTotalDeductions().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEpfContribution() != null ? payRun.getTotalEpfContribution().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEsiContribution() != null ? payRun.getTotalEsiContribution().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEdliContribution() != null ? payRun.getTotalEdliContribution().doubleValue() : 0.0,
                    currencyStyle);
            createCurrencyCell(row, col++,
                    payRun.getTotalEpfAdminCharges() != null ? payRun.getTotalEpfAdminCharges().doubleValue() : 0.0,
                    currencyStyle);
        }
    }

    // ========== HELPER METHODS ==========
    private void addSummaryRow(Sheet sheet, int rowNum, String label, String value, String remarks) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
        row.createCell(2).setCellValue(remarks != null ? remarks : "");
    }

    private void addSummaryCurrencyRow(Sheet sheet, int rowNum, String label,
            BigDecimal value, CellStyle currencyStyle, String remarks) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);

        Cell valueCell = row.createCell(1);
        if (value != null) {
            valueCell.setCellValue(value.doubleValue());
        } else {
            valueCell.setCellValue(0.0);
        }
        valueCell.setCellStyle(currencyStyle);

        row.createCell(2).setCellValue(remarks != null ? remarks : "");
    }

    private void addEmployeeTotalsRow(Sheet sheet, int rowNum, List<EmployeePayRun> employeePayRuns,
            CellStyle currencyStyle) {
        Row totalRow = sheet.createRow(rowNum);

        // Calculate totals
        double totalEarnings = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getTotalEarnings() != null ? emp.getTotalEarnings() : 0.0)
                .sum();

        double totalDeductions = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getTotalDeductions() != null ? emp.getTotalDeductions() : 0.0)
                .sum();

        double totalTaxes = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getTotalTaxes() != null ? emp.getTotalTaxes() : 0.0)
                .sum();

        double totalBenefits = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getTotalBenefits() != null ? emp.getTotalBenefits() : 0.0)
                .sum();

        double totalReimbursements = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getTotalReimbursements() != null ? emp.getTotalReimbursements() : 0.0)
                .sum();

        double totalNetPay = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getNetPay() != null ? emp.getNetPay() : 0.0)
                .sum();

        double totalMonthlySalary = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getMonthlySalary() != null ? emp.getMonthlySalary() : 0.0)
                .sum();

        double totalLOP = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getLOP() != null ? emp.getLOP() : 0.0)
                .sum();

        double totalMonthlyTds = employeePayRuns.stream()
                .mapToDouble(emp -> emp.getMonthlyTds() != null ? emp.getMonthlyTds() : 0.0)
                .sum();

        // Create "GRAND TOTAL" label
        Cell labelCell = totalRow.createCell(0);
        labelCell.setCellValue("GRAND TOTAL");
        CellStyle boldStyle = sheet.getWorkbook().createCellStyle();
        Font boldFont = sheet.getWorkbook().createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);
        labelCell.setCellStyle(boldStyle);

        // Add totals at appropriate columns
        createCurrencyCell(totalRow, 7, totalEarnings, currencyStyle);
        createCurrencyCell(totalRow, 8, totalDeductions, currencyStyle);
        createCurrencyCell(totalRow, 9, totalTaxes, currencyStyle);
        createCurrencyCell(totalRow, 10, totalBenefits, currencyStyle);
        createCurrencyCell(totalRow, 11, totalReimbursements, currencyStyle);
        createCurrencyCell(totalRow, 12, totalNetPay, currencyStyle);
        createCurrencyCell(totalRow, 13, totalMonthlySalary, currencyStyle);
        createCurrencyCell(totalRow, 16, totalLOP, currencyStyle);
        createCurrencyCell(totalRow, 17, totalMonthlyTds, currencyStyle);
    }

    private void autoSizeAllSheets(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            for (int j = 0; j < sheet.getRow(0).getLastCellNum(); j++) {
                sheet.autoSizeColumn(j);
            }
        }
    }

    // ========== ORIGINAL METHODS (KEEP THESE) ==========
    public byte[] generatePayRunReport(PayRun payRun, List<EmployeePayRun> employeePayRuns) throws IOException {
        String method = "generatePayRunReport";
        log.info("[{}] 📊 Generating Excel report for payrunId={}", method, payRun.getPayrunId());

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            Sheet summarySheet = workbook.createSheet("PayRun Summary");
            createPayRunSummarySheet(summarySheet, payRun, headerStyle, currencyStyle, dateStyle);

            Sheet employeeSheet = workbook.createSheet("Employee PayRuns");
            createEmployeePayRunsSheet(employeeSheet, employeePayRuns, headerStyle, currencyStyle);

            autoSizeColumns(summarySheet, employeeSheet);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("[{}] ✅ Excel report generated successfully for payrunId={}",
                    method, payRun.getPayrunId());

            return outputStream.toByteArray();
        }
    }

    private void createPayRunSummarySheet(Sheet sheet, PayRun payRun,
            CellStyle headerStyle, CellStyle currencyStyle, CellStyle dateStyle) {

        Row headerRow = sheet.createRow(0);
        String[] headers = { "Field", "Value" };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        addDetailRow(sheet, rowNum++, "PayRun ID", payRun.getPayrunId());
        addDetailRow(sheet, rowNum++, "Type", payRun.getType() != null ? payRun.getType().name() : "");
        addDetailRow(sheet, rowNum++, "Status", payRun.getStatus() != null ? payRun.getStatus().name() : "");
        addDetailRow(sheet, rowNum++, "Payment Status", payRun.getPaymentStatus());

        if (payRun.getPayPeriodStartDate() != null) {
            addDetailRow(sheet, rowNum++, "Period Start", payRun.getPayPeriodStartDate().format(DATE_FORMATTER));
        }
        if (payRun.getPayPeriodEndDate() != null) {
            addDetailRow(sheet, rowNum++, "Period End", payRun.getPayPeriodEndDate().format(DATE_FORMATTER));
        }
        if (payRun.getPayDate() != null) {
            addDetailRow(sheet, rowNum++, "Pay Date", payRun.getPayDate().format(DATE_FORMATTER));
        }

        addDetailRow(sheet, rowNum++, "Processing Period", payRun.getProcessingPeriod());
        addDetailRow(sheet, rowNum++, "No. of Employees",
                payRun.getNoOfEmployees() != null ? payRun.getNoOfEmployees().toString() : "0");

        addCurrencyRow(sheet, rowNum++, "Total Net Pay", payRun.getTotalNetPay(), currencyStyle);
        addCurrencyRow(sheet, rowNum++, "Total Taxes", payRun.getTotalTaxes(), currencyStyle);
        addCurrencyRow(sheet, rowNum++, "Total Benefits", payRun.getTotalBenefits(), currencyStyle);
        addCurrencyRow(sheet, rowNum++, "Total Deductions", payRun.getTotalDeductions(), currencyStyle);
        addCurrencyRow(sheet, rowNum++, "Total Payroll Cost", payRun.getTotalPayrollCost(), currencyStyle);
        addCurrencyRow(sheet, rowNum++, "Payroll Total", payRun.getPayrollTotal(), currencyStyle);

        addDetailRow(sheet, rowNum++, "Approval Type", payRun.getApprovalType());
        addDetailRow(sheet, rowNum++, "Compensation Name", payRun.getCompensationName());
        addDetailRow(sheet, rowNum++, "Has Direct Deposit",
                payRun.getHasDirectDepositPayments() != null ? payRun.getHasDirectDepositPayments().toString() : "");
        addDetailRow(sheet, rowNum++, "Rejected Reason", payRun.getRejectedReason());
    }

    private void createEmployeePayRunsSheet(Sheet sheet, List<EmployeePayRun> employeePayRuns,
            CellStyle headerStyle, CellStyle currencyStyle) {

        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Employee Number", "Employee Name",
                "Payment Status", "Payment Mode",
                "Total Earnings", "Total Deductions", "Total Taxes", "Monthly TDS",
                "Total Benefits", "Total Reimbursements",
                "Net Pay", "Monthly Salary", "Paid Days",
                "Employee Status", "Leaves Taken (Current Pay Period)", "Bonus Eligible", "Tax Overridden"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (EmployeePayRun empPayRun : employeePayRuns) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;

            // row.createCell(col++).setCellValue(empPayRun.getEmployeeId() != null ?
            // empPayRun.getEmployeeId() : "");
            row.createCell(col++)
                    .setCellValue(empPayRun.getEmployeeNumber() != null ? empPayRun.getEmployeeNumber() : "");
            row.createCell(col++).setCellValue(empPayRun.getEmployeeName() != null ? empPayRun.getEmployeeName() : "");
            row.createCell(col++)
                    .setCellValue(empPayRun.getPaymentStatus() != null ? empPayRun.getPaymentStatus() : "");
            row.createCell(col++).setCellValue(empPayRun.getPaymentMode() != null ? empPayRun.getPaymentMode() : "");

            createCurrencyCell(row, col++, empPayRun.getTotalEarnings(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalDeductions(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalTaxes(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getMonthlyTds(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalBenefits(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getTotalReimbursements(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getNetPay(), currencyStyle);
            createCurrencyCell(row, col++, empPayRun.getMonthlySalary(), currencyStyle);

            row.createCell(col++).setCellValue(empPayRun.getPaidDays() != null ? empPayRun.getPaidDays() : 0.0);
            row.createCell(col++)
                    .setCellValue(empPayRun.getEmployeeStatus() != null ? empPayRun.getEmployeeStatus() : "");
            row.createCell(col++)
                    .setCellValue(empPayRun.getTotalNoOfLeaves() != null ? empPayRun.getTotalNoOfLeaves() : 0.0);
            row.createCell(col++)
                    .setCellValue(empPayRun.getBonusEarningExistForEmployee() != null
                            ? empPayRun.getBonusEarningExistForEmployee()
                            : false);
            row.createCell(col++)
                    .setCellValue(empPayRun.getTaxOverridden() != null ? empPayRun.getTaxOverridden() : false);
        }
    }

    private void addDetailRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private void addCurrencyRow(Sheet sheet, int rowNum, String label,
            BigDecimal value, CellStyle currencyStyle) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(currencyStyle);
    }

    private void autoSizeColumns(Sheet... sheets) {
        for (Sheet sheet : sheets) {
            for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }

    // ========== SINGLE PAY RUN REPORT ==========
    /**
     * Generate Excel report for a SINGLE pay run (for monthly download)
     */
    public byte[] generateSinglePayRunReport(String organizationId, String payrunId) throws IOException {
        String method = "generateSinglePayRunReport";
        log.info("[{}] 📊 Generating Excel report for single pay run | orgId={} | payrunId={}",
                method, organizationId, payrunId);

        // FIX: You need to implement proper pay run fetching
        PayRun payRun = fetchPayRun(organizationId, payrunId);
        if (payRun == null) {
            throw new RuntimeException("PayRun not found with ID: " + payrunId);
        }

        List<EmployeePayRun> employeePayRuns = employeePayRunRepository.findByPayrunId(payrunId);
        log.info("[{}] ✅ Found pay run {} with {} employees", method, payrunId, employeePayRuns.size());

        return generatePayRunReport(payRun, employeePayRuns);
    }

    // FIX: Implement this properly using repository
    private PayRun fetchPayRun(String organizationId, String payrunId) {
        return payRunRepository
                .findByOrganizationIdAndPayrunId(organizationId, payrunId)
                .orElseThrow(() -> new RuntimeException(
                        "PayRun not found for orgId=" + organizationId +
                                ", payrunId=" + payrunId));
    }

}