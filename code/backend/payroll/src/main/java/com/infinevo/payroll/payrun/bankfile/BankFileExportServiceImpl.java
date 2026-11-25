package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.report.CsvWriter;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.core.report.ReportColumn;
import com.infinevo.core.report.ReportWriter;
import com.infinevo.core.report.XlsxStreamingWriter;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateException.Kind;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankFileExportServiceImpl implements BankFileExportService {

    static final int PREVIEW_ROWS = 20;
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Logger log = LoggerFactory.getLogger(BankFileExportServiceImpl.class);

    private final BankFileService bankFileService;
    private final BankFileTemplateService templateService;
    private final BankFileExportRepository exportRepository;
    private final DocumentService documentService;

    public BankFileExportServiceImpl(
            BankFileService bankFileService,
            BankFileTemplateService templateService,
            BankFileExportRepository exportRepository,
            DocumentService documentService) {
        this.bankFileService = bankFileService;
        this.templateService = templateService;
        this.exportRepository = exportRepository;
        this.documentService = documentService;
    }

    @Override
    @Transactional(readOnly = true)
    public BankFilePreviewResponse preview(UUID payrunId, BankFileExportRequest request) {
        BankFileTemplateResponse template = template(request);
        BankFileRun run = bankFileService.run(payrunId, selection(request));
        List<List<String>> lines = lines(template, run, today());
        return new BankFilePreviewResponse(
                template.name(),
                template.columns().stream().map(BankFileColumn::heading).toList(),
                lines.subList(0, Math.min(PREVIEW_ROWS, lines.size())),
                lines.size(),
                run.fileTotal(),
                run.selectedNetPay(),
                run.runNetPay(),
                run.omissions());
    }

    @Override
    public BankFileExportResponse generate(UUID payrunId, BankFileExportRequest request) {
        UUID tenantId = TenantContext.require();
        BankFileTemplateResponse template = template(request);
        Set<UUID> selection = selection(request);
        BankFileRun run = bankFileService.run(payrunId, selection);
        List<List<String>> lines = lines(template, run, today());
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("No employee in this selection can be paid by bank transfer");
        }
        byte[] bytes = write(template, lines);
        String fileName = fileName(template, run.payRun());
        // A file the bank gets must be one the history shows, so a storage failure fails the request.
        UUID documentId = documentService.store(DocumentKind.EXPORT, null, fileName, new ByteArrayInputStream(bytes));
        BankFileExport export = exportRepository.saveAndFlush(new BankFileExport(
                tenantId,
                payrunId,
                template.builtIn() ? null : UUID.fromString(template.id()),
                template.name(),
                documentId,
                fileName,
                lines.size(),
                run.fileTotal(),
                selection == null ? null : selection.size(),
                BankFileTemplateServiceImpl.currentActor()));
        log.info(
                "Pay run {}: bank file {} made from template {} with {} row(s); {} left out",
                payrunId,
                export.getId(),
                template.name(),
                lines.size(),
                run.omissions().size());
        return BankFileExportResponse.from(export);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankFileExportResponse> list(UUID payrunId) {
        return exportRepository
                .findByTenantIdAndPayrunIdOrderByCreatedAtDesc(TenantContext.require(), payrunId)
                .stream()
                .map(BankFileExportResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Download download(UUID payrunId, UUID exportId) {
        BankFileExport export = exportRepository
                .findByIdAndTenantIdAndPayrunId(exportId, TenantContext.require(), payrunId)
                .orElseThrow(() -> new BankFileTemplateException(Kind.NOT_FOUND, "No bank file " + exportId));
        return new Download(BankFileExportResponse.from(export), documentService.open(export.getDocumentId()));
    }

    private BankFileTemplateResponse template(BankFileExportRequest request) {
        if (request == null
                || request.templateId() == null
                || request.templateId().isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        return templateService.get(request.templateId().trim());
    }

    private static Set<UUID> selection(BankFileExportRequest request) {
        if (request == null
                || request.employeeIds() == null
                || request.employeeIds().isEmpty()) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        for (UUID id : request.employeeIds()) {
            if (id != null) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /** One list of cell texts per row, in template column order. Rows stay in W-88's employee-number order. */
    static List<List<String>> lines(BankFileTemplateResponse template, BankFileRun run, LocalDate today) {
        List<List<String>> lines = new ArrayList<>(run.rows().size());
        int serial = 1;
        for (BankFileRow row : run.rows()) {
            Employee employee = run.employees().get(row.employeeId());
            List<String> cells = new ArrayList<>(template.columns().size());
            for (BankFileColumn column : template.columns()) {
                cells.add(BankFileValues.value(column, serial, row, employee, run.payRun(), today));
            }
            lines.add(cells);
            serial++;
        }
        return lines;
    }

    static byte[] write(BankFileTemplateResponse template, List<List<String>> lines) {
        List<ReportColumn> columns = new ArrayList<>();
        for (int c = 0; c < template.columns().size(); c++) {
            columns.add(ReportColumn.text("c" + c, template.columns().get(c).heading()));
        }
        List<Map<String, Object>> rows = new ArrayList<>(lines.size());
        for (List<String> line : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < line.size(); c++) {
                row.put("c" + c, line.get(c));
            }
            rows.add(row);
        }
        ReportWriter writer = template.fileFormat() == ExportFormat.XLSX ? new XlsxStreamingWriter() : new CsvWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writer.write(columns, rows.stream(), out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the bank file", e);
        }
        byte[] bytes = out.toByteArray();
        if (template.includeHeader()) {
            return bytes;
        }
        return template.fileFormat() == ExportFormat.XLSX ? dropXlsxHeader(bytes) : dropCsvHeader(bytes);
    }

    /** The CSV writer always writes BOM + header + CRLF; headings are one line (validated), so cut that line. */
    static byte[] dropCsvHeader(byte[] csv) {
        String text = new String(csv, StandardCharsets.UTF_8);
        int end = text.indexOf("\r\n");
        if (end < 0) {
            return csv;
        }
        String bom = text.startsWith("\uFEFF") ? "\uFEFF" : "";
        return (bom + text.substring(end + 2)).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] dropXlsxHeader(byte[] xlsx) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getRow(0) != null) {
                sheet.removeRow(sheet.getRow(0));
            }
            if (sheet.getLastRowNum() >= 1) {
                sheet.shiftRows(1, sheet.getLastRowNum(), -1);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the bank file", e);
        }
    }

    static String fileName(BankFileTemplateResponse template, PayRun run) {
        String base = template.name().replaceAll("[^A-Za-z0-9_-]", "_");
        String extension = template.fileFormat() == ExportFormat.XLSX ? ".xlsx" : ".csv";
        return base + "_" + run.getPeriod() + extension;
    }

    private static LocalDate today() {
        return LocalDate.now(INDIA);
    }
}
