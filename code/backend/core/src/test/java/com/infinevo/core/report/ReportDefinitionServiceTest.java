package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.ActionRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-23.1 spec section 6 — a definition names a registered source, chooses columns only from its
 * allow-list, names only filters it declares and a real required action; a system definition cannot be
 * edited; and the list shows a caller only what they could run.
 */
class ReportDefinitionServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ReportDefinitionRepository definitions;
    private ActionRepository actions;
    private PermissionService permissions;
    private ReportDefinitionService service;

    @BeforeEach
    void setUp() {
        definitions = mock(ReportDefinitionRepository.class);
        actions = mock(ActionRepository.class);
        permissions = mock(PermissionService.class);
        ReportSourceRegistry sources =
                new ReportSourceRegistry(List.of(new ReportSourceRegistryTest.FakeSource("employee")));
        service = new ReportDefinitionServiceImpl(definitions, sources, actions, permissions);
        when(actions.existsById("core.employee.export")).thenReturn(true);
        when(definitions.saveAndFlush(any(ReportDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A valid definition is created with its columns in the order given")
    void createsAValidDefinition() {
        ReportDefinitionResponse created = service.create(request("active-staff", List.of("status", "name")));

        assertThat(created.code()).isEqualTo("active-staff");
        assertThat(created.columns()).containsExactly("status", "name");
        assertThat(created.system()).isFalse();
    }

    @Test
    @DisplayName("A column outside the source's allow-list is refused, naming it - a definition is never a query")
    void unknownColumnIsRefused() {
        assertThatThrownBy(() -> service.create(request("x", List.of("name", "salary"))))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors().get("columns")).contains("salary"));
        verify(definitions, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("An unknown source, an unknown action, a filter the source does not take: each a field error")
    void otherFieldErrors() {
        ReportDefinitionRequest bad = new ReportDefinitionRequest(
                "x", "X", "payroll_run", List.of("name"), Map.of(), ExportFormat.CSV, "core.nothing.here");
        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKeys("source", "requiredAction"));

        ReportDefinitionRequest badFilter = new ReportDefinitionRequest(
                "x",
                "X",
                "employee",
                List.of("name"),
                Map.of("department", "sales"),
                ExportFormat.CSV,
                "core.employee.export");
        assertThatThrownBy(() -> service.create(badFilter))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKey("defaultFilters"));
    }

    @Test
    @DisplayName("Repeated or blank columns, a bad code and no format are refused")
    void shapeErrors() {
        assertThatThrownBy(() -> service.create(request("x", List.of("name", "name"))))
                .isInstanceOf(ReportDefinitionService.ValidationException.class);
        assertThatThrownBy(() -> service.create(request("Not A Code", List.of("name"))))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKey("code"));
        assertThatThrownBy(() -> service.create(new ReportDefinitionRequest(
                        "x", "X", "employee", List.of("name"), null, null, "core.employee.export")))
                .isInstanceOfSatisfying(
                        ReportDefinitionService.ValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKey("format"));
    }

    @Test
    @DisplayName("A code already used in this tenant is a 409")
    void duplicateCodeIsAConflict() {
        when(definitions.existsByTenantIdAndCode(TENANT, "active-staff")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("active-staff", List.of("name"))))
                .isInstanceOf(ReportDefinitionService.DuplicateCodeException.class);
    }

    @Test
    @DisplayName("A system definition cannot be edited")
    void systemDefinitionIsReadOnly() {
        ReportDefinition seeded = definition("employees", "core.employee.export");
        ReflectionTestUtils.setField(seeded, "system", true);
        UUID id = UUID.randomUUID();
        when(definitions.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(seeded));

        assertThatThrownBy(() -> service.update(id, request("employees", List.of("name"))))
                .isInstanceOf(ReportDefinitionService.SystemDefinitionException.class);
        verify(definitions, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("The list omits a definition whose required action the caller lacks")
    void listHidesWhatTheCallerCannotRun() {
        when(definitions.findByTenantIdOrderByCodeAsc(TENANT))
                .thenReturn(List.of(
                        definition("audit-log", "core.audit.read"), definition("employees", "core.employee.export")));
        when(permissions.holds(anyString())).thenReturn(false);
        when(permissions.holds("core.employee.export")).thenReturn(true);

        assertThat(service.list()).extracting(ReportDefinitionResponse::code).containsExactly("employees");
    }

    private static ReportDefinitionRequest request(String code, List<String> columns) {
        return new ReportDefinitionRequest(
                code,
                "Name",
                "employee",
                columns,
                Map.of("status", "ACTIVE"),
                ExportFormat.XLSX,
                "core.employee.export");
    }

    static ReportDefinition definition(String code, String requiredAction) {
        ReportDefinition definition = new ReportDefinition(TENANT, code, "test");
        definition.apply(code, "employee", List.of("name", "status"), null, ExportFormat.CSV, requiredAction, "test");
        return definition;
    }
}
