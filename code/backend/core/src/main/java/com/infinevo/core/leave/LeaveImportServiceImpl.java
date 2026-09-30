package com.infinevo.core.leave;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link LeaveImportService} (W-16.4b, spec section 4 &amp; 9).
 *
 * <p><strong>CRITICAL:</strong> This class is deliberately NOT annotated with {@code @Transactional}.
 * Individual row allocations are executed in separate {@code REQUIRES_NEW} transactions via
 * {@link LeaveImportAllocationHelper} to guarantee partial success — invalid or duplicate rows
 * never roll back successfully imported rows.
 */
@Service
public class LeaveImportServiceImpl implements LeaveImportService {

    private static final Logger log = LoggerFactory.getLogger(LeaveImportServiceImpl.class);

    private final DocumentService documentService;
    private final LeaveImportLogRepository importLogRepository;
    private final LeaveImportRowValidator rowValidator;
    private final LeaveImportAllocationHelper allocationHelper;

    public LeaveImportServiceImpl(
            DocumentService documentService,
            LeaveImportLogRepository importLogRepository,
            LeaveImportRowValidator rowValidator,
            LeaveImportAllocationHelper allocationHelper) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.importLogRepository = Objects.requireNonNull(importLogRepository, "importLogRepository must not be null");
        this.rowValidator = Objects.requireNonNull(rowValidator, "rowValidator must not be null");
        this.allocationHelper = Objects.requireNonNull(allocationHelper, "allocationHelper must not be null");
    }

    @Override
    public LeaveImportResultResponse importLeaves(UUID tenantId, UUID documentId, String leaveYear, boolean dryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(leaveYear, "leaveYear must not be null");

        // 1. Open document stream via DocumentService
        DocumentService.DocumentContent docContent = documentService.open(documentId);

        // 2. Parse CSV
        List<LeaveImportRow> parsedRows = new ArrayList<>();
        List<LeaveImportError> parseErrors = new ArrayList<>();

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
                    // Skip header row if present
                    if (isHeaderRow(line)) {
                        continue;
                    }
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] tokens = parseCsvLine(trimmed);
                if (tokens.length >= 3) {
                    parsedRows.add(new LeaveImportRow(lineNumber, tokens[0], tokens[1], tokens[2]));
                } else {
                    parseErrors.add(new LeaveImportError(
                            lineNumber,
                            tokens.length > 0 ? tokens[0] : "",
                            tokens.length > 1 ? tokens[1] : "",
                            "",
                            "INVALID_FORMAT"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to read CSV stream for document {}", documentId, e);
            throw new IllegalStateException("Failed to parse import document CSV: " + e.getMessage(), e);
        }

        int totalRows = parsedRows.size() + parseErrors.size();

        // 3. Create initial import log record
        LeaveImportLog importLog = new LeaveImportLog(tenantId, documentId, leaveYear, dryRun, totalRows);
        importLog = allocationHelper.saveLog(importLog);

        // 4. Validate rows
        LeaveImportRowValidator.ValidationResult validation = rowValidator.validate(tenantId, parsedRows);
        List<LeaveImportError> allErrors = new ArrayList<>(parseErrors);
        allErrors.addAll(validation.errors());

        int rowsImported = 0;

        // 5. Create allocations if not dry run
        if (!dryRun) {
            LocalDate[] dates = resolveYearDates(leaveYear);
            for (LeaveImportRowValidator.ValidatedRow validRow : validation.validRows()) {
                try {
                    LeaveAllocationRequest allocRequest = new LeaveAllocationRequest(
                            validRow.employee().getId(),
                            validRow.leaveType().getId(),
                            leaveYear,
                            dates[0],
                            dates[1],
                            validRow.days());
                    allocationHelper.createOneAllocation(tenantId, allocRequest);
                    rowsImported++;
                } catch (Exception e) {
                    log.warn(
                            "Allocation creation failed for line {}: {}",
                            validRow.row().lineNumber(),
                            e.getMessage());
                    allErrors.add(new LeaveImportError(
                            validRow.row().lineNumber(),
                            validRow.row().employeeNumber(),
                            validRow.row().leaveTypeCode(),
                            validRow.row().days(),
                            "DUPLICATE_ALLOCATION"));
                }
            }
        }

        int rowsFailed = allErrors.size();

        // 6. Generate and store error document if any errors occurred
        if (!allErrors.isEmpty()) {
            allErrors.sort(Comparator.comparingInt(LeaveImportError::lineNumber));
            StringBuilder sb = new StringBuilder();
            sb.append("line_number,employee_number,leave_type_code,days,error_reason\n");
            for (LeaveImportError err : allErrors) {
                sb.append(err.lineNumber())
                        .append(",")
                        .append(escapeCsv(err.employeeNumber()))
                        .append(",")
                        .append(escapeCsv(err.leaveTypeCode()))
                        .append(",")
                        .append(escapeCsv(err.days()))
                        .append(",")
                        .append(err.reason())
                        .append("\n");
            }

            byte[] errorBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            UUID errorDocId = documentService.store(
                    DocumentKind.EXPORT,
                    null,
                    "leave-import-errors-" + importLog.getId() + ".csv",
                    new ByteArrayInputStream(errorBytes));
            importLog.setErrorDocumentId(errorDocId);
        }

        // 7. Update final status and counts
        ImportStatus finalStatus;
        if (rowsFailed == 0) {
            finalStatus = ImportStatus.COMPLETED;
        } else {
            finalStatus = ImportStatus.COMPLETED_WITH_ERRORS;
        }

        importLog.setRowsImported(rowsImported);
        importLog.setRowsFailed(rowsFailed);
        importLog.setStatus(finalStatus);
        importLog.setFinishedAt(Instant.now());

        importLog = allocationHelper.saveLog(importLog);

        return LeaveImportResultResponse.from(importLog);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public LeaveImportResultResponse getImport(UUID tenantId, UUID importId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(importId, "importId must not be null");
        LeaveImportLog logEntity = importLogRepository
                .findByTenantIdAndId(tenantId, importId)
                .orElseThrow(() -> new IllegalArgumentException("Leave import not found: " + importId));
        return LeaveImportResultResponse.from(logEntity);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<LeaveImportResultResponse> listImports(UUID tenantId, Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        return importLogRepository
                .findByTenantIdOrderByStartedAtDesc(tenantId, pageable)
                .map(LeaveImportResultResponse::from);
    }

    private boolean isHeaderRow(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("employee") || lower.contains("emp_no") || lower.contains("leave_type");
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
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private LocalDate[] resolveYearDates(String leaveYear) {
        Objects.requireNonNull(leaveYear, "leaveYear must not be null");
        String trimmed = leaveYear.trim();
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            int startYear = Integer.parseInt(parts[0].trim());
            int endYear;
            if (parts[1].trim().length() == 2) {
                endYear = (startYear / 100) * 100 + Integer.parseInt(parts[1].trim());
            } else {
                endYear = Integer.parseInt(parts[1].trim());
            }
            return new LocalDate[] {LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31)};
        } else {
            int year = Integer.parseInt(trimmed);
            return new LocalDate[] {LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        }
    }
}
