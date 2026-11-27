package com.infinevo.payroll.payrun.bankfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** W-88.2 spec sections 4.1–4.3 — the file a template makes. */
class BankFileExportServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.randomUUID();
    private static final UUID ASHA = UUID.randomUUID();
    private static final UUID RAVI = UUID.randomUUID();

    private BankFileService bankFileService;
    private BankFileTemplateService templateService;
    private BankFileExportRepository exportRepository;
    private DocumentService documentService;
    private BankFileExportServiceImpl service;
    private PayRun payRun;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        bankFileService = mock(BankFileService.class);
        templateService = mock(BankFileTemplateService.class);
        exportRepository = mock(BankFileExportRepository.class);
        documentService = mock(DocumentService.class);
        when(exportRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new BankFileExportServiceImpl(bankFileService, templateService, exportRepository, documentService);

        payRun = mock(PayRun.class);
        when(payRun.getPeriod()).thenReturn(YearMonth.of(2026, 11));
        when(payRun.getPaidOn()).thenReturn(LocalDate.of(2026, 11, 30));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private BankFileRun run() {
        Employee asha = mock(Employee.class);
        when(asha.getFirstName()).thenReturn("Asha");
        when(asha.getLastName()).thenReturn("Rao");
        when(asha.getWorkEmail()).thenReturn("asha@acme.test");
        BankFileRow ashaRow = new BankFileRow(
                ASHA,
                "E-101",
                "Asha Rao",
                "Asha Rao",
                "000123",
                "HDFC0001234",
                "HDFC",
                "SAVINGS",
                "BANK_TRANSFER",
                new BigDecimal("65000.5000"),
                "Salary 2026-11",
                "NEFT",
                "Salary");
        BankFileRow raviRow = new BankFileRow(
                RAVI,
                "E-102",
                "Ravi Menon",
                "R MENON",
                "998877",
                "ICIC0000456",
                "ICICI",
                "SALARY",
                "BANK_TRANSFER",
                new BigDecimal("1234.4900"),
                "Salary 2026-11",
                "RTGS",
                "Consultant fees");
        return new BankFileRun(
                payRun,
                List.of(ashaRow, raviRow),
                Map.of(ASHA, asha),
                List.of(new BankFileOmission("E-103", "payment mode is CASH")),
                new BigDecimal("76234.9900"),
                new BigDecimal("76234.9900"));
    }

    private static BankFileTemplateResponse template(
            ExportFormat format, boolean header, List<BankFileColumn> columns) {
        return new BankFileTemplateResponse(
                UUID.randomUUID().toString(), "HDFC bulk", format, header, columns, false, null, null);
    }

    private static BankFileColumn field(String heading, BankFileField field, String format) {
        return new BankFileColumn(heading, BankFileColumnSource.FIELD, field, null, format);
    }

    @Test
    @DisplayName("cells follow the template: serial, fixed text, fields, each amount and date format")
    void linesFollowTemplate() {
        BankFileTemplateResponse template = template(
                ExportFormat.CSV,
                true,
                List.of(
                        new BankFileColumn("#", BankFileColumnSource.SERIAL, null, null, null),
                        new BankFileColumn("Mode", BankFileColumnSource.FIXED, null, "NEFT", null),
                        field("Holder", BankFileField.ACCOUNT_HOLDER_NAME, null),
                        field("A/c", BankFileField.ACCOUNT_NUMBER, null),
                        field("IFSC", BankFileField.IFSC, null),
                        field("Amt", BankFileField.NET_PAY, null),
                        field("Rs", BankFileField.NET_PAY, "WHOLE_RUPEES"),
                        field("Paise", BankFileField.NET_PAY, "PAISE"),
                        field("Paid", BankFileField.PAID_ON, "ddMMyyyy"),
                        field("Month", BankFileField.PAY_PERIOD, "MMM yyyy"),
                        field("Value", BankFileField.VALUE_DATE, "yyyy-MM-dd"),
                        field("First", BankFileField.FIRST_NAME, null),
                        field("Email", BankFileField.WORK_EMAIL, null)));

        List<List<String>> lines = BankFileExportServiceImpl.lines(template, run(), LocalDate.of(2026, 12, 1));

        assertThat(lines.get(0))
                .containsExactly(
                        "1",
                        "NEFT",
                        "Asha Rao",
                        "000123",
                        "HDFC0001234",
                        "65000.50",
                        "65001",
                        "6500050",
                        "30112026",
                        "Nov 2026",
                        "2026-12-01",
                        "Asha",
                        "asha@acme.test");
        // Ravi has no employee record in the map: employee-only fields are blank, not an error.
        assertThat(lines.get(1).subList(0, 8))
                .containsExactly("2", "NEFT", "R MENON", "998877", "ICIC0000456", "1234.49", "1234", "123449");
        assertThat(lines.get(1).get(11)).isEmpty();
    }

    @Test
    @DisplayName("transfer type and credit narration come from each employee's Bank tab")
    void transferTypeAndCreditNarration() {
        BankFileTemplateResponse template = template(
                ExportFormat.XLSX,
                true,
                List.of(
                        field("Type", BankFileField.TRANSFER_TYPE, null),
                        field("Narration", BankFileField.CREDIT_NARRATION, null)));

        List<List<String>> lines = BankFileExportServiceImpl.lines(template, run(), LocalDate.of(2026, 12, 1));

        assertThat(lines.get(0)).containsExactly("NEFT", "Salary");
        assertThat(lines.get(1)).containsExactly("RTGS", "Consultant fees");
    }

    @Test
    @DisplayName("CSV keeps the header when asked and drops only that line when not; BOM stays")
    void csvHeader() {
        List<BankFileColumn> columns = List.of(field("A/c", BankFileField.ACCOUNT_NUMBER, null));
        List<List<String>> lines = List.of(List.of("000123"), List.of("998877"));

        String with = new String(
                BankFileExportServiceImpl.write(template(ExportFormat.CSV, true, columns), lines),
                StandardCharsets.UTF_8);
        String without = new String(
                BankFileExportServiceImpl.write(template(ExportFormat.CSV, false, columns), lines),
                StandardCharsets.UTF_8);

        assertThat(with).isEqualTo("\uFEFFA/c\r\n000123\r\n998877\r\n");
        assertThat(without).isEqualTo("\uFEFF000123\r\n998877\r\n");
    }

    @Test
    @DisplayName("XLSX without a header starts with the first employee; account numbers stay text")
    void xlsxHeader() throws Exception {
        List<BankFileColumn> columns = List.of(field("A/c", BankFileField.ACCOUNT_NUMBER, null));
        byte[] bytes = BankFileExportServiceImpl.write(
                template(ExportFormat.XLSX, false, columns), List.of(List.of("000123"), List.of("998877")));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("000123");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("998877");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("generate passes the selection on, stores an EXPORT with no employee, and records the file")
    void generateStoresAndRecords() {
        BankFileTemplateResponse template =
                template(ExportFormat.CSV, true, List.of(field("Amt", BankFileField.NET_PAY, null)));
        when(templateService.get(template.id())).thenReturn(template);
        BankFileRun run = run();
        when(bankFileService.run(eq(RUN_ID), eq(Set.of(ASHA, RAVI)))).thenReturn(run);
        UUID documentId = UUID.randomUUID();
        when(documentService.store(
                        eq(DocumentKind.EXPORT), isNull(), eq("HDFC_bulk_2026-11.csv"), any(InputStream.class)))
                .thenReturn(documentId);

        BankFileExportResponse export =
                service.generate(RUN_ID, new BankFileExportRequest(template.id(), List.of(ASHA, RAVI)));

        ArgumentCaptor<BankFileExport> saved = ArgumentCaptor.forClass(BankFileExport.class);
        verify(exportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(export.rowCount()).isEqualTo(2);
        assertThat(export.fileTotal()).isEqualByComparingTo("66234.99");
        assertThat(export.selectedEmployeeCount()).isEqualTo(2);
        assertThat(export.templateName()).isEqualTo("HDFC bulk");
    }

    @Test
    @DisplayName("the built-in is recorded with no template id; no selection means everyone")
    void builtInRecordsNoTemplateId() {
        when(templateService.get("generic-neft")).thenReturn(BankFileTemplateServiceImpl.BUILT_IN);
        BankFileRun run = run();
        when(bankFileService.run(RUN_ID, null)).thenReturn(run);
        when(documentService.store(any(), any(), any(), any(InputStream.class))).thenReturn(UUID.randomUUID());

        BankFileExportResponse export = service.generate(RUN_ID, new BankFileExportRequest("generic-neft", null));

        assertThat(export.templateId()).isNull();
        assertThat(export.selectedEmployeeCount()).isNull();
        assertThat(export.fileName()).isEqualTo("Generic_bulk_NEFT_2026-11.csv");
    }

    @Test
    @DisplayName("a selection nobody in it can be paid is refused and nothing is stored")
    void emptyFileRefused() {
        BankFileTemplateResponse template =
                template(ExportFormat.CSV, true, List.of(field("Amt", BankFileField.NET_PAY, null)));
        when(templateService.get(template.id())).thenReturn(template);
        when(bankFileService.run(eq(RUN_ID), any()))
                .thenReturn(new BankFileRun(
                        payRun,
                        List.of(),
                        Map.of(),
                        List.of(new BankFileOmission("E-103", "payment mode is CASH")),
                        BigDecimal.TEN,
                        BigDecimal.ONE));

        assertThatThrownBy(() -> service.generate(RUN_ID, new BankFileExportRequest(template.id(), List.of(RAVI))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No employee");
        verify(documentService, never()).store(any(), any(), any(), any(InputStream.class));
    }

    @Test
    @DisplayName("preview returns the headings, at most 20 rows, the totals and who is left out")
    void preview() {
        BankFileTemplateResponse template =
                template(ExportFormat.CSV, true, List.of(field("No", BankFileField.EMPLOYEE_NUMBER, null)));
        when(templateService.get(template.id())).thenReturn(template);
        BankFileRun run = run();
        when(bankFileService.run(RUN_ID, null)).thenReturn(run);

        BankFilePreviewResponse preview = service.preview(RUN_ID, new BankFileExportRequest(template.id(), List.of()));

        assertThat(preview.headings()).containsExactly("No");
        assertThat(preview.rows()).containsExactly(List.of("E-101"), List.of("E-102"));
        assertThat(preview.rowCount()).isEqualTo(2);
        assertThat(preview.omitted()).extracting(BankFileOmission::reason).containsExactly("payment mode is CASH");
        assertThat(preview.runNetPay()).isEqualByComparingTo("76234.99");
    }

    @Test
    @DisplayName("templateId is required")
    void templateRequired() {
        assertThatThrownBy(() -> service.preview(RUN_ID, new BankFileExportRequest(" ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateId");
    }
}
