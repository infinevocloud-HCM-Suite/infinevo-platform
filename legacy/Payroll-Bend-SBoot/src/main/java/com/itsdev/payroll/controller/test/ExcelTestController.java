package com.itsdev.payroll.controller.test;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;
import com.itsdev.payroll.service.ExcelReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/test/excel")
public class ExcelTestController {

    private static final Logger log = LoggerFactory.getLogger(ExcelTestController.class);
    private final ExcelReportService excelReportService;

    public ExcelTestController(ExcelReportService excelReportService) {
        this.excelReportService = excelReportService;
    }

    @GetMapping("/generate-test-report")
    public ResponseEntity<byte[]> generateTestExcelReport() {
        String method = "generateTestExcelReport";
        log.info("[{}] 🧪 Generating test Excel report with dummy data", method);

        try {
            // 1. Create dummy PayRun
            PayRun dummyPayRun = createDummyPayRun();

            // 2. Create dummy EmployeePayRun records
            List<EmployeePayRun> dummyEmployeePayRuns = createDummyEmployeePayRuns();

            // 3. Generate Excel
            byte[] excelData = excelReportService.generatePayRunReport(dummyPayRun, dummyEmployeePayRuns);

            log.info("[{}] ✅ Test Excel generated successfully! Size: {} bytes", method, excelData.length);

            // 4. Return as downloadable file
            String fileName = "test_payrun_report_" + System.currentTimeMillis() + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (IOException e) {
            log.error("[{}] ❌ Error generating test Excel: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("Error: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            log.error("[{}] ❌ Unexpected error: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("Unexpected error: " + e.getMessage()).getBytes());
        }
    }

    private PayRun createDummyPayRun() {
        PayRun payRun = new PayRun();

        // Basic info
        payRun.setPayrunId("TEST-" + System.currentTimeMillis());
        payRun.setType(PayRunType.REGULAR);
        payRun.setStatus(PayRunStatus.APPROVED);
        payRun.setPaymentStatus("COMPLETED");

        // Dates
        payRun.setPayPeriodStartDate(LocalDate.now().minusMonths(1).withDayOfMonth(1));
        payRun.setPayPeriodEndDate(
                LocalDate.now().minusMonths(1).withDayOfMonth(LocalDate.now().minusMonths(1).lengthOfMonth()));
        payRun.setPayDate(LocalDate.now());
        payRun.setProcessingPeriod("December 2024");
        payRun.setApprovedDate(LocalDate.now().minusDays(5));

        // Counts
        payRun.setNoOfEmployees(5);
        payRun.setNoOfSkippedEmployees(0);

        // Financials
        payRun.setTotalNetPay(new BigDecimal("250000.00"));
        payRun.setTotalTaxes(new BigDecimal("45000.00"));
        payRun.setTotalBenefits(new BigDecimal("15000.00"));
        payRun.setTotalDeductions(new BigDecimal("20000.00"));
        payRun.setTotalPayrollCost(new BigDecimal("270000.00"));
        payRun.setPayrollTotal(new BigDecimal("250000.00"));

        // Flags
        payRun.setCanEditPaydate(false);
        payRun.setCanPostPayrunTransactions(true);
        payRun.setHasDirectDepositPayments(true);
        payRun.setHasNonDirectDepositPayments(false);

        // Other info
        payRun.setApprovalType("AUTO");
        payRun.setCompensationName("Monthly Salary");
        payRun.setRejectedReason(null);
        payRun.setStatusInfo("All payments processed successfully");

        // Create dummy organization (just for reference)
        Organization org = new Organization();
        org.setOrganizationName("Test Company Pvt. Ltd.");
        payRun.setOrganization(org);

        return payRun;
    }

    private List<EmployeePayRun> createDummyEmployeePayRuns() {
        List<EmployeePayRun> employeePayRuns = new ArrayList<>();

        // Employee 1: Manager
        employeePayRuns.add(createDummyEmployee(
                "EMP001",
                "E001",
                "John Doe",
                "PAID",
                "BANK_TRANSFER",
                75000.00,
                12000.00,
                15000.00,
                5000.00,
                3000.00,
                71000.00,
                75000.00,
                30.0,
                "ACTIVE",
                2,
                false,
                false));

        // Employee 2: Senior Developer
        employeePayRuns.add(createDummyEmployee(
                "EMP002",
                "E002",
                "Jane Smith",
                "PAID",
                "BANK_TRANSFER",
                60000.00,
                10000.00,
                12000.00,
                4000.00,
                2000.00,
                56000.00,
                60000.00,
                30.0,
                "ACTIVE",
                1,
                true,
                false));

        // Employee 3: Junior Developer
        employeePayRuns.add(createDummyEmployee(
                "EMP003",
                "E003",
                "Robert Johnson",
                "PAID",
                "BANK_TRANSFER",
                40000.00,
                6000.00,
                8000.00,
                2000.00,
                1000.00,
                37000.00,
                40000.00,
                28.0, // Had 2 leaves
                "ACTIVE",
                2,
                false,
                false));

        // Employee 4: HR Executive
        employeePayRuns.add(createDummyEmployee(
                "EMP004",
                "E004",
                "Sarah Williams",
                "YET_TO_PAY",
                "CASH",
                35000.00,
                5000.00,
                7000.00,
                1500.00,
                800.00,
                32300.00,
                35000.00,
                30.0,
                "ACTIVE",
                0,
                false,
                true));

        // Employee 5: Intern
        employeePayRuns.add(createDummyEmployee(
                "EMP005",
                "E005",
                "Mike Brown",
                "PAID",
                "BANK_TRANSFER",
                20000.00,
                3000.00,
                4000.00,
                1000.00,
                500.00,
                18500.00,
                20000.00,
                30.0,
                "PROBATION",
                0,
                false,
                false));

        return employeePayRuns;
    }

    private EmployeePayRun createDummyEmployee(
            String employeeId,
            String employeeNumber,
            String employeeName,
            String paymentStatus,
            String paymentMode,
            Double totalEarnings,
            Double totalDeductions,
            Double totalTaxes,
            Double totalBenefits,
            Double totalReimbursements,
            Double netPay,
            Double monthlySalary,
            Double paidDays,
            String employeeStatus,
            double leavesTaken,
            Boolean bonusEligible,
            Boolean taxOverridden) {

        EmployeePayRun emp = new EmployeePayRun();

        emp.setEmployeeId(employeeId);
        emp.setEmployeeNumber(employeeNumber);
        emp.setEmployeeName(employeeName);
        emp.setFullName(employeeName);
        emp.setPaymentStatus(paymentStatus);
        emp.setPaymentMode(paymentMode);
        emp.setTotalEarnings(totalEarnings);
        emp.setTotalDeductions(totalDeductions);
        emp.setTotalTaxes(totalTaxes);
        emp.setTotalBenefits(totalBenefits);
        emp.setTotalReimbursements(totalReimbursements);
        emp.setNetPay(netPay);
        emp.setMonthlySalary(monthlySalary);
        emp.setPaidDays(paidDays);
        emp.setEmployeeStatus(employeeStatus);
        emp.setTotalNoOfLeaves(leavesTaken);
        emp.setBonusEarningExistForEmployee(bonusEligible);
        emp.setTaxOverridden(taxOverridden);
        emp.setEmployeeHavingHoldSalary(false);

        return emp;
    }
}