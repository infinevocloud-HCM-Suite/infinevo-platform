package com.infinevo.payroll.payslip;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayslipPdfWriterTest {

    private final PayslipPdfWriter writer = new PayslipPdfWriter();

    @Test
    @DisplayName("generates valid PDF starting with %PDF and containing employee name, period and net pay (W-98.4 §7)")
    void generatesValidPdfWithRequiredContent() throws Exception {
        UUID payrunId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        PayslipResponse.RunSummary run = new PayslipResponse.RunSummary(
                payrunId, "REGULAR", "2026-10", LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), "PAID");
        PayslipResponse.EmployerSummary employer = new PayslipResponse.EmployerSummary("Acme Global Inc");
        PayslipResponse.EmployeeSummary employee = new PayslipResponse.EmployeeSummary(
                employeeId, "EMP-4242", "Radha Sharma", "Lead Architect", "Engineering", LocalDate.of(2024, 1, 15));
        PayslipResponse.DaysSummary days = new PayslipResponse.DaysSummary(
                new BigDecimal("31.00"), new BigDecimal("31.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        List<PayslipLineResponse> earnings = List.of(
                new PayslipLineResponse("BASIC", "Basic Salary", new BigDecimal("120000.00"), "SALARY", true),
                new PayslipLineResponse("HRA", "House Rent Allowance", new BigDecimal("48000.00"), "SALARY", false));

        List<PayslipLineResponse> deductions = List.of(
                new PayslipLineResponse("PF", "Provident Fund", new BigDecimal("1800.00"), "STATUTORY", false),
                new PayslipLineResponse("PT", "Professional Tax", new BigDecimal("200.00"), "STATUTORY", false));

        PayslipResponse.TotalsSummary totals = new PayslipResponse.TotalsSummary(
                new BigDecimal("168000.00"),
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("166000.00"));

        PayslipResponse response = new PayslipResponse(
                run, employer, employee, days, earnings, deductions, List.of(), List.of(), totals, null);

        byte[] pdfBytes = writer.write(response);

        // 1. Must not be empty and must start with %PDF magic header
        assertThat(pdfBytes).isNotNull().isNotEmpty();
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF");

        // 2. OpenPDF can parse the generated binary PDF
        PdfReader reader = new PdfReader(pdfBytes);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);

        // 3. Inspect page content text
        byte[] pageBytes = reader.getPageContent(1);
        String pageContent = new String(pageBytes, StandardCharsets.ISO_8859_1);

        // Content must contain employee name, period and net pay
        assertThat(pageContent).contains("Radha Sharma");
        assertThat(pageContent).contains("2026-10");
        assertThat(pageContent).contains("166000.00");
        assertThat(pageContent).contains("Acme Global Inc");
        assertThat(pageContent).contains("Basic Salary");

        reader.close();
    }
}
