package com.infinevo.core.report;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs an export (W-23.1, spec section 3):
 *
 * <pre>
 * definition (bound tenant) → required_action → source by code → columns from the allow-list
 *   → rows streamed inside a read-only transaction → CSV or streaming xlsx, into a temp file
 *   → DocumentService.storeFile(EXPORT, no employee) → a fifteen-minute signed link
 * </pre>
 *
 * <p><strong>Nothing is held whole.</strong> The source returns a lazy stream, the writer pulls one row
 * at a time into a temporary file, and the document store streams that file to storage
 * ({@code storeFile}, decision D2). The temporary file is deleted however the export ends.
 *
 * <p><strong>The transaction is read-only and ends before storage.</strong> A JPA result stream needs
 * an open connection while it is read, so the write happens inside one; the upload does not, and the
 * document store opens its own. Holding a database connection across a blob upload would tie a pool
 * slot to the network.
 */
@Service
public class ExportServiceImpl implements ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ReportDefinitionRepository definitions;
    private final ReportSourceRegistry sources;
    private final PermissionService permissions;
    private final DocumentService documents;
    private final DocumentLinkService links;
    private final TransactionTemplate readOnly;
    private final Function<ExportFormat, ReportWriter> writers;
    private final Clock clock;

    @Autowired
    public ExportServiceImpl(
            ReportDefinitionRepository definitions,
            ReportSourceRegistry sources,
            PermissionService permissions,
            DocumentService documents,
            DocumentLinkService links,
            PlatformTransactionManager transactionManager) {
        this(
                definitions,
                sources,
                permissions,
                documents,
                links,
                readOnlyTemplate(transactionManager),
                ReportWriter::of,
                Clock.systemUTC());
    }

    ExportServiceImpl(
            ReportDefinitionRepository definitions,
            ReportSourceRegistry sources,
            PermissionService permissions,
            DocumentService documents,
            DocumentLinkService links,
            TransactionTemplate readOnly,
            Function<ExportFormat, ReportWriter> writers,
            Clock clock) {
        this.definitions = Objects.requireNonNull(definitions, "definitions must not be null");
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.documents = Objects.requireNonNull(documents, "documents must not be null");
        this.links = Objects.requireNonNull(links, "links must not be null");
        this.readOnly = Objects.requireNonNull(readOnly, "readOnly must not be null");
        this.writers = Objects.requireNonNull(writers, "writers must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ExportResponse export(UUID definitionId, Map<String, String> filters) {
        UUID tenantId = TenantContext.require();
        if (definitionId == null) {
            throw new ReportDefinitionService.ValidationException(Map.of("definitionId", "definitionId is required"));
        }
        ReportDefinition definition = readOnly.execute(status -> definitions
                .findByIdAndTenantId(definitionId, tenantId)
                .orElseThrow(() -> new ReportDefinitionService.NotFoundException(definitionId)));

        // The second gate. core.report.read got the caller to the endpoint; this one says whether they
        // may see what this particular definition exports (spec section 6).
        permissions.require(definition.getRequiredAction());

        ReportSource source = sources.find(definition.getSource())
                .orElseThrow(() -> new ReportDefinitionService.ValidationException(Map.of(
                        "definitionId",
                        "Definition " + definition.getCode() + " names source " + definition.getSource()
                                + ", which is not registered in this runtime")));
        List<ReportColumn> columns = columnsOf(definition, source);
        ReportFilters merged = ReportFilters.of(definition.getDefaultFilters(), filters);
        List<String> unknown = merged.values().keySet().stream()
                .filter(f -> !source.filterNames().contains(f))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new ReportDefinitionService.ValidationException(
                    Map.of("filters", "Source " + source.code() + " takes no filter " + unknown));
        }

        ExportFormat format = definition.getFormat();
        String fileName = definition.getCode() + "-" + STAMP.format(clock.instant()) + "." + format.extension();
        Path file = createTempFile(format);
        try {
            long rowCount = readOnly.execute(status -> write(source, merged, columns, format, file));
            UUID documentId = documents.storeFile(DocumentKind.EXPORT, null, fileName, file);
            DocumentLinkService.SignedLink link = links.signedLink(documentId);
            log.info(
                    "Exported {} ({} rows, {}) as document {} in tenant {}",
                    definition.getCode(),
                    rowCount,
                    format,
                    documentId,
                    tenantId);
            return new ExportResponse(documentId, fileName, format, rowCount, link.url(), link.expiresAt());
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * The definition's columns in its order, each resolved against the source's allow-list. A name the
     * source no longer offers — the source changed after the definition was saved — is refused rather
     * than written as an empty column.
     */
    static List<ReportColumn> columnsOf(ReportDefinition definition, ReportSource source) {
        Map<String, ReportColumn> allowed =
                source.columns().stream().collect(Collectors.toMap(ReportColumn::name, Function.identity()));
        List<String> missing = definition.getColumns().stream()
                .filter(name -> !allowed.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new ReportDefinitionService.ValidationException(Map.of(
                    "columns",
                    "Definition " + definition.getCode() + " names columns source " + source.code() + " does not have: "
                            + String.join(", ", missing)));
        }
        return definition.getColumns().stream().map(allowed::get).toList();
    }

    private long write(
            ReportSource source, ReportFilters filters, List<ReportColumn> columns, ExportFormat format, Path file) {
        try (Stream<Map<String, Object>> rows = source.rows(filters);
                OutputStream out = Files.newOutputStream(file)) {
            return writers.apply(format).write(columns, rows, out);
        } catch (IOException e) {
            throw new UncheckedIOException("The export file could not be written", e);
        }
    }

    private static Path createTempFile(ExportFormat format) {
        try {
            return Files.createTempFile("infinevo-export-", "." + format.extension());
        } catch (IOException e) {
            throw new UncheckedIOException("No temporary file could be created for the export", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete the temporary export file {}", file, e);
        }
    }

    private static TransactionTemplate readOnlyTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }
}
