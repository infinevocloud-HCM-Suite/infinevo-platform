package com.infinevo.payroll.payslip;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Renders a payslip as a binary PDF using OpenPDF (W-98.4 §4).
 * Amounts stay BigDecimal and are formatted to 2 decimal places.
 */
@Component
public class PayslipPdfWriter {

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public byte[] write(PayslipResponse payslip) {
        Objects.requireNonNull(payslip, "payslip must not be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

            // Employer & Title
            String employer = payslip.employer() != null && payslip.employer().name() != null
                    ? payslip.employer().name()
                    : "Infinevo";
            Paragraph empPara = new Paragraph(employer, titleFont);
            empPara.setAlignment(Element.ALIGN_CENTER);
            document.add(empPara);

            String period = payslip.run() != null && payslip.run().period() != null
                    ? payslip.run().period()
                    : "";
            Paragraph titlePara = new Paragraph("Payslip for the Period " + period, headerFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(15);
            document.add(titlePara);

            // Employee Details Table
            PdfPTable empTable = new PdfPTable(4);
            empTable.setWidthPercentage(100);
            empTable.setSpacingAfter(15);

            String empName = payslip.employee() != null && payslip.employee().name() != null
                    ? payslip.employee().name()
                    : "";
            String empNumber = payslip.employee() != null && payslip.employee().number() != null
                    ? payslip.employee().number()
                    : "";
            String dept = payslip.employee() != null && payslip.employee().department() != null
                    ? payslip.employee().department()
                    : "";
            String desig = payslip.employee() != null && payslip.employee().designation() != null
                    ? payslip.employee().designation()
                    : "";

            addCell(empTable, "Employee Name:", boldFont);
            addCell(empTable, empName, normalFont);
            addCell(empTable, "Employee ID:", boldFont);
            addCell(empTable, empNumber, normalFont);

            addCell(empTable, "Department:", boldFont);
            addCell(empTable, dept, normalFont);
            addCell(empTable, "Designation:", boldFont);
            addCell(empTable, desig, normalFont);

            if (payslip.days() != null) {
                addCell(empTable, "Payable Days:", boldFont);
                addCell(empTable, formatAmount(payslip.days().payableDays()), normalFont);
                addCell(empTable, "Paid Days:", boldFont);
                addCell(empTable, formatAmount(payslip.days().paidDays()), normalFont);
            }
            document.add(empTable);

            // Earnings & Deductions Table
            PdfPTable breakdownTable = new PdfPTable(2);
            breakdownTable.setWidthPercentage(100);
            breakdownTable.setSpacingAfter(15);

            // Left: Earnings Table
            PdfPTable earningsTable = new PdfPTable(2);
            PdfPCell eHeader = new PdfPCell(new Phrase("Earnings", boldFont));
            eHeader.setColspan(2);
            eHeader.setBackgroundColor(new Color(240, 240, 240));
            earningsTable.addCell(eHeader);

            if (payslip.earnings() != null) {
                for (PayslipLineResponse line : payslip.earnings()) {
                    addCell(earningsTable, line.name(), normalFont);
                    addRightCell(earningsTable, formatAmount(line.amount()), normalFont);
                }
            }
            BigDecimal gross = payslip.totals() != null ? payslip.totals().grossEarnings() : BigDecimal.ZERO;
            addCell(earningsTable, "Gross Earnings", boldFont);
            addRightCell(earningsTable, formatAmount(gross), boldFont);

            // Right: Deductions Table
            PdfPTable deductionsTable = new PdfPTable(2);
            PdfPCell dHeader = new PdfPCell(new Phrase("Deductions", boldFont));
            dHeader.setColspan(2);
            dHeader.setBackgroundColor(new Color(240, 240, 240));
            deductionsTable.addCell(dHeader);

            if (payslip.deductions() != null) {
                for (PayslipLineResponse line : payslip.deductions()) {
                    addCell(deductionsTable, line.name(), normalFont);
                    addRightCell(deductionsTable, formatAmount(line.amount()), normalFont);
                }
            }
            BigDecimal totalDed = payslip.totals() != null ? payslip.totals().totalDeductions() : BigDecimal.ZERO;
            addCell(deductionsTable, "Total Deductions", boldFont);
            addRightCell(deductionsTable, formatAmount(totalDed), boldFont);

            PdfPCell leftCell = new PdfPCell(earningsTable);
            leftCell.setBorder(PdfPCell.NO_BORDER);
            leftCell.setPaddingRight(5);
            breakdownTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(deductionsTable);
            rightCell.setBorder(PdfPCell.NO_BORDER);
            rightCell.setPaddingLeft(5);
            breakdownTable.addCell(rightCell);

            document.add(breakdownTable);

            // Net Pay Summary Box
            BigDecimal netPay = payslip.totals() != null ? payslip.totals().netPay() : BigDecimal.ZERO;
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            netTable.setSpacingAfter(15);
            PdfPCell netLabelCell = new PdfPCell(new Phrase("Net Salary Payable:", headerFont));
            netLabelCell.setBackgroundColor(new Color(245, 245, 245));
            netLabelCell.setPadding(8);
            netTable.addCell(netLabelCell);
            PdfPCell netValCell = new PdfPCell(new Phrase(formatAmount(netPay), headerFont));
            netValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            netValCell.setBackgroundColor(new Color(245, 245, 245));
            netValCell.setPadding(8);
            netTable.addCell(netValCell);
            document.add(netTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate payslip PDF", e);
        }
    }

    private static void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static void addRightCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(4);
        table.addCell(cell);
    }
}
