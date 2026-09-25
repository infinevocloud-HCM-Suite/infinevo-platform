package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-23.1 spec section 7 — a column outside the source's allow-list is refused; the format is honoured;
 * an empty result is a header-only file; an unknown source is refused; the file is stored as
 * {@code EXPORT} with no employee. Plus the second gate: the definition's {@code required_action}.
 */
class ExportServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private ReportDefinitionRepository definitions;
    private PermissionService permissions;
    private DocumentService documents;
    private DocumentLinkService links;
    private List<Map<String, Object>> rows;
    private List<ExportFormat> formatsUsed;
    private AtomicReference<String> storedContent;
    private AtomicReference<Path> storedFile;
    private ExportServiceImpl service;
    private UUID definitionId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        definitions = mock(ReportDefinitionRepository.class);
        permissions = mock(PermissionService.class);
        documents = mock(DocumentService.class);
        links = mock(DocumentLinkService.class);
        rows = new ArrayList<>();
        formatsUsed = new ArrayList<>();
        storedContent = new AtomicReference<>();
        storedFile = new AtomicReference<>();
        definitionId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        ReportSourceRegistry sources =
                new ReportSourceRegistry(List.of(new ReportSourceRegistryTest.FakeSource("employee", rows)));
        TransactionTemplate readOnly = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new ExportServiceImpl(
                definitions,
                sources,
                permissions,
                documents,
                links,
                readOnly,
                format -> {
                    formatsUsed.add(format);
                    return ReportWriter.of(format);
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(documents.storeFile(eq(DocumentKind.EXPORT), isNull(), any(), any()))
                .thenAnswer(inv -> {
                    Path file = inv.getArgument(3);
                    storedFile.set(file);
                    // new String, not Files.readString: an xlsx is binary, and readString throws on it.
                    storedContent.set(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                    return documentId;
                });
        when(links.signedLink(documentId))
                .thenReturn(new DocumentLinkService.SignedLink("/api/v1/documents/download?t=x", NOW.plusSeconds(900)));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A CSV definition writes CSV, stores it as an EXPORT with no employee, and returns a link")
    void exportsThroughTheDocumentStore() {
        definition(ExportFormat.CSV, List.of("status", "name"), Map.of());
        rows.add(Map.of("name", "Asha", "status", "ACTIVE"));

        ExportService.ExportResponse response = service.export(definitionId, null);

        assertThat(formatsUsed).containsExactly(ExportFormat.CSV);
        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.fileName()).isEqualTo("active-staff-20260925-100000.csv");
        assertThat(response.url()).isEqualTo("/api/v1/documents/download?t=x");
        assertThat(storedContent.get()).isEqualTo(CsvWriter.BYTE_ORDER_MARK + "Status,Name\r\nACTIVE,Asha\r\n");
        verify(documents).storeFile(eq(DocumentKind.EXPORT), isNull(), eq("active-staff-20260925-100000.csv"), any());
    }

    @Test
    @DisplayName("An XLSX definition uses the streaming xlsx writer")
    void formatIsHonoured() {
        definition(ExportFormat.XLSX, List.of("name"), Map.of());

        ExportService.ExportResponse response = service.export(definitionId, Map.of());

        assertThat(formatsUsed).containsExactly(ExportFormat.XLSX);
        assertThat(response.fileName()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("An empty result is a header-only file, not an error")
    void emptyResultIsHeaderOnly() {
        definition(ExportFormat.CSV, List.of("name"), Map.of());

        ExportService.ExportResponse response = service.export(definitionId, null);

        assertThat(response.rowCount()).isZero();
        assertThat(storedContent.get()).isEqualTo(CsvWriter.BYTE_ORDER_MARK + "Name\r\n");
    }

    @Test
    @DisplayName("The request's filters override the definition's defaults")
    void filtersOverrideDefaults() {
        definition(ExportFormat.CSV, List.of("name"), Map.of("status", "ACTIVE"));
        rows.add(Map.of("name", "Asha", "status", "ACTIVE"));
        rows.add(Map.of("name", "Bharat", "status", "TERMINATED"));

        assertThat(service.export(definitionId, null).rowCount()).isEqualTo(1);
        assertThat(service.export(definitionId, Map.of("status", "TERMINATED")).rowCount())
                .isEqualTo(1);
        assertThat(storedContent.get()).contains("Bharat").doesNotContain("Asha");
    }

    @Test
    @DisplayName("A filter the source does not declare is refused, not ignored")
    void unknownFilterIsRefused() {
        definition(ExportFormat.CSV, List.of("name"), Map.of());

        assertThatThrownBy(() -> service.export(definitionId, Map.of("salary", "high")))
                .isInstanceOf(ReportDefinitionService.ValidationException.class);
        verify(documents, never()).storeFile(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A column the source no longer offers is refused")
    void columnOutsideTheAllowListIsRefused() {
        definition(ExportFormat.CSV, List.of("name", "salary"), Map.of());

        assertThatThrownBy(() -> service.export(definitionId, null))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors().get("columns")).contains("salary"));
    }

    @Test
    @DisplayName("A source not registered in this runtime is refused")
    void unknownSourceIsRefused() {
        ReportDefinition definition = ReportDefinitionServiceTest.definition("x", "core.employee.export");
        definition.apply("x", "payroll_run", List.of("name"), null, ExportFormat.CSV, "core.employee.export", "t");
        when(definitions.findByIdAndTenantId(definitionId, TENANT)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.export(definitionId, null))
                .isInstanceOf(ReportDefinitionService.ValidationException.class);
    }

    @Test
    @DisplayName("Without the definition's required action: 403, and nothing is written or stored")
    void requiredActionIsTheSecondGate() {
        definition(ExportFormat.CSV, List.of("name"), Map.of());
        doThrow(new PermissionDeniedException("core.employee.export"))
                .when(permissions)
                .require("core.employee.export");

        assertThatThrownBy(() -> service.export(definitionId, null)).isInstanceOf(PermissionDeniedException.class);
        assertThat(formatsUsed).isEmpty();
        verify(documents, never()).storeFile(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A definition not in the bound tenant is not found")
    void unknownDefinitionIsNotFound() {
        assertThatThrownBy(() -> service.export(UUID.randomUUID(), null))
                .isInstanceOf(ReportDefinitionService.NotFoundException.class);
    }

    @Test
    @DisplayName("The temporary file is deleted after the export is stored")
    void temporaryFileIsDeleted() {
        definition(ExportFormat.CSV, List.of("name"), Map.of());

        service.export(definitionId, null);

        assertThat(storedFile.get()).isNotNull();
        assertThat(Files.exists(storedFile.get())).isFalse();
    }

    @Test
    @DisplayName("The response's toString withholds the link, which carries its signature")
    void responseToStringWithholdsTheUrl() {
        definition(ExportFormat.CSV, List.of("name"), Map.of());

        assertThat(service.export(definitionId, null).toString())
                .doesNotContain("?t=")
                .contains("<withheld>");
    }

    private void definition(ExportFormat format, List<String> columns, Map<String, String> defaults) {
        ReportDefinition definition = ReportDefinitionServiceTest.definition("active-staff", "core.employee.export");
        definition.apply("Active staff", "employee", columns, defaults, format, "core.employee.export", "t");
        when(definitions.findByIdAndTenantId(definitionId, TENANT)).thenReturn(Optional.of(definition));
    }
}
