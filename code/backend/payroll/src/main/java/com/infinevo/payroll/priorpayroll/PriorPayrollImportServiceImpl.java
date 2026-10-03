package com.infinevo.payroll.priorpayroll;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.tenant.TenantContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PriorPayrollImportService} (W-38.1 §4 &amp; §9).
 *
 * <p><strong>CRITICAL:</strong> This class is deliberately NOT annotated with {@code @Transactional}.
 * Individual row inserts are executed in separate {@code REQUIRES_NEW} transactions via
 * {@link PriorPayrollImportWriter} to guarantee partial success — invalid or duplicate rows
 * never roll back successfully imported rows.
 */
@Service
public class PriorPayrollImportServiceImpl implements PriorPayrollImportService {

    private static final Logger log = LoggerFactory.getLogger(PriorPayrollImportServiceImpl.class);

    private static final String CSV_TEMPLATE_HEADER =
            "employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay\n";

    private final DocumentService documentService;
    private final PriorPayrollImportLogRepository importLogRepository;
    private final PriorPayrollRowValidator rowValidator;
    private final PriorPayrollImportWriter importWriter;

    public PriorPayrollImportServiceImpl(
            DocumentService documentService,
            PriorPayrollImportLogRepository importLogRepository,
            PriorPayrollRowValidator rowValidator,
            PriorPayrollImportWriter importWriter) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.importLogRepository = Objects.requireNonNull(importLogRepository, "importLogRepository must not be null");
        this.rowValidator = Objects.requireNonNull(rowValidator, "rowValidator must not be null");
        this.importWriter = Objects.requireNonNull(importWriter, "importWriter must not be null");
    }

    @Override
    public PriorPayrollImportResponse importFile(UUID documentId, String financialYear, boolean dryRun) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        // 1. Create initial import log record in PENDING state
        PriorPayrollImportLog importLog = new PriorPayrollImportLog(tenantId, documentId, financialYear, dryRun, 0);
        try {
            importLog = importWriter.saveLog(importLog);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Import document not found: " + documentId, e);
        }

        // 2. Open document stream via DocumentService
        DocumentService.DocumentContent docContent;
        try {
            docContent = documentService.open(documentId);
        } catch (Exception e) {
            log.error("Failed to open document {}", documentId, e);
            importLog.setStatus(PriorPayrollImportStatus.FAILED);
            importLog.setFinishedAt(Instant.now());
            importWriter.saveLog(importLog);
            throw new IllegalStateException("Failed to open import document: " + e.getMessage(), e);
        }

        // 3. Parse CSV
        List<PriorPayrollRow> parsedRows = new ArrayList<>();
        List<PriorPayrollRowError> parseErrors = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(docContent.content(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (firstLine) {
                    firstLine = false;
                    // Strip optional UTF-8 BOM
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    if (isHeaderRow(line)) {
                        continue;
                    }
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] tokens = parseCsvLine(trimmed);
                if (tokens.length >= 8) {
                    parsedRows.add(new PriorPayrollRow(
                            lineNumber,
                            tokens[0],
                            tokens[1],
                            tokens[2],
                            tokens[3],
                            tokens[4],
                            tokens[5],
                            tokens[6],
                            tokens[7]));
                } else {
                    parseErrors.add(new PriorPayrollRowError(
                            lineNumber,
                            tokens.length > 0 ? tokens[0] : "",
                            tokens.length > 1 ? tokens[1] : "",
                            "INVALID_FORMAT"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to read CSV stream for document {}", documentId, e);
            importLog.setStatus(PriorPayrollImportStatus.FAILED);
            importLog.setFinishedAt(Instant.now());
            importWriter.saveLog(importLog);
            throw new IllegalStateException("Failed to parse import document CSV: " + e.getMessage(), e);
        }

        int totalRows = parsedRows.size() + parseErrors.size();
        importLog.setRowsTotal(totalRows);
        importLog = importWriter.saveLog(importLog);

        List<PriorPayrollRowError> allErrors = new ArrayList<>(parseErrors);
        int rowsImported = 0;

        try {
            // 4. Validate rows
            PriorPayrollRowValidator.ValidationResult validation =
                    rowValidator.validate(tenantId, financialYear, parsedRows);
            allErrors.addAll(validation.errors());

            // 5. Insert rows if not dry run
            if (!dryRun) {
                String actor = currentActor();
                for (PriorPayrollRowValidator.ValidatedPriorPayrollRow v : validation.validRows()) {
                    PriorPayrollMonth m = new PriorPayrollMonth(
                            tenantId,
                            v.employee().getId(),
                            v.period(),
                            v.grossEarnings(),
                            v.epfEmployee(),
                            v.esiEmployee(),
                            v.professionalTax(),
                            v.tds(),
                            v.netPay(),
                            importLog.getId(),
                            actor);
                    try {
                        importWriter.insertOne(m);
                        rowsImported++;
                    } catch (DataIntegrityViolationException e) {
                        allErrors.add(new PriorPayrollRowError(
                                v.rawRow().lineNumber(),
                                v.rawRow().employeeNumber(),
                                v.rawRow().period(),
                                "ALREADY_IMPORTED"));
                    } catch (RuntimeException e) {
                        log.error(
                                "Failed to insert prior payroll row line {}",
                                v.rawRow().lineNumber(),
                                e);
                        allErrors.add(new PriorPayrollRowError(
                                v.rawRow().lineNumber(),
                                v.rawRow().employeeNumber(),
                                v.rawRow().period(),
                                "ALREADY_IMPORTED"));
                    }
                }
            }
        } catch (RuntimeException e) {
            int written = rowsImported;
            log.error("Prior payroll import {} failed after {} row(s) were written", importLog.getId(), written, e);
            importLog.setRowsImported(written);
            importLog.setRowsFailed(Math.max(totalRows - written, 0));
            importLog.setStatus(PriorPayrollImportStatus.FAILED);
            importLog.setFinishedAt(Instant.now());
            importWriter.saveLog(importLog);
            throw e;
        }

        int rowsFailed = allErrors.size();

        // 6. Generate and store error document if any errors occurred
        if (!allErrors.isEmpty()) {
            allErrors.sort(Comparator.comparingInt(PriorPayrollRowError::lineNumber));
            StringBuilder sb = new StringBuilder();
            sb.append("line_number,employee_number,period,error_reason\n");
            for (PriorPayrollRowError err : allErrors) {
                sb.append(err.lineNumber())
                        .append(",")
                        .append(escapeCsv(err.employeeNumber()))
                        .append(",")
                        .append(escapeCsv(err.period()))
                        .append(",")
                        .append(err.reason())
                        .append("\n");
            }

            byte[] errorBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            try {
                UUID errorDocId = documentService.store(
                        DocumentKind.EXPORT,
                        null,
                        "prior-payroll-errors-" + importLog.getId() + ".csv",
                        new ByteArrayInputStream(errorBytes));
                importLog.setErrorDocumentId(errorDocId);
            } catch (RuntimeException e) {
                log.error("Could not store the error report for prior payroll import {}", importLog.getId(), e);
            }
        }

        // 7. Update final status and counts
        PriorPayrollImportStatus finalStatus;
        if (rowsFailed == 0) {
            finalStatus = PriorPayrollImportStatus.COMPLETED;
        } else {
            finalStatus = PriorPayrollImportStatus.COMPLETED_WITH_ERRORS;
        }

        importLog.setRowsImported(rowsImported);
        importLog.setRowsFailed(rowsFailed);
        importLog.setStatus(finalStatus);
        importLog.setFinishedAt(Instant.now());

        importLog = importWriter.saveLog(importLog);

        return PriorPayrollImportResponse.from(importLog);
    }

    // The reads need a transaction of their own: the tenant binding is transaction-local, and in
    // auto-commit TenantBindingDataSourceProxy refuses it (RLS would return nothing). Only the class
    // stays non-transactional, for the import's per-row writes.
    @Override
    @Transactional(readOnly = true)
    public PriorPayrollImportResponse getImport(UUID id) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(id, "id must not be null");
        PriorPayrollImportLog logEntity = importLogRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new PriorPayrollNotFoundException(id));
        return PriorPayrollImportResponse.from(logEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PriorPayrollImportResponse> listImports(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(pageable, "pageable must not be null");
        return importLogRepository
                .findByTenantIdOrderByStartedAtDesc(tenantId, pageable)
                .map(PriorPayrollImportResponse::from);
    }

    @Override
    public String template() {
        return CSV_TEMPLATE_HEADER;
    }

    private boolean isHeaderRow(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("employee_number") || lower.contains("gross_earnings") || lower.contains("net_pay");
    }

    private String[] parseCsvLine(String line) {
        String[] raw = line.split(",", -1);
        String[] cleaned = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String token = raw[i].trim();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                token = token.substring(1, token.length() - 1).trim();
            }
            cleaned[i] = token;
        }
        return cleaned;
    }

    private String escapeCsv(String val) {
        if (val == null) {
            return "";
        }
        if (!val.isEmpty() && "=+-@\t\r".indexOf(val.charAt(0)) >= 0) {
            val = "'" + val;
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private String currentActor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }
}
