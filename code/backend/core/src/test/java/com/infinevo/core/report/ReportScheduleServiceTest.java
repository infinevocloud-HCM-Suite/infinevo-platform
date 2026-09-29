package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.UserAccountIdResolver;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-23.2 spec section 4 — a schedule names a definition in the tenant whose {@code required_action}
 * the caller holds, a cadence with a day that can occur, filters its source declares and real
 * addresses; {@code PUT} creates or replaces under the client's id.
 */
class ReportScheduleServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalTime NINE = LocalTime.of(9, 0);

    private ReportScheduleRepository schedules;
    private ReportDefinitionRepository definitions;
    private PermissionService permissions;
    private ReportScheduleService service;
    private UUID definitionId;

    @BeforeEach
    void setUp() {
        schedules = mock(ReportScheduleRepository.class);
        definitions = mock(ReportDefinitionRepository.class);
        permissions = mock(PermissionService.class);
        ReportSourceRegistry sources =
                new ReportSourceRegistry(List.of(new ReportSourceRegistryTest.FakeSource("employee")));
        service = new ReportScheduleServiceImpl(
                schedules, definitions, sources, permissions, mock(UserAccountIdResolver.class), new ObjectMapper());

        definitionId = UUID.randomUUID();
        ReportDefinition definition = ReportDefinitionServiceTest.definition("employees", "core.employee.export");
        ReflectionTestUtils.setField(definition, "id", definitionId);
        ReflectionTestUtils.setField(definition, "source", "employee");
        when(definitions.findByIdAndTenantId(definitionId, TENANT)).thenReturn(Optional.of(definition));
        when(schedules.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        when(schedules.saveAndFlush(any(ReportSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A new schedule is created under the client's id, as a new row, with the addresses normalised")
    void createsUnderTheClientsId() {
        UUID id = UUID.randomUUID();

        ReportScheduleService.ReportScheduleResponse saved = service.put(
                id, request("DAILY", 5, Map.of("status", "ACTIVE"), "Finance@Acme.test; audit@outside.test"));

        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.cadence()).isEqualTo("DAILY");
        assertThat(saved.dayOfPeriod()).as("a DAILY schedule has no day").isNull();
        assertThat(saved.recipients()).containsExactly("finance@acme.test", "audit@outside.test");
        assertThat(saved.filters()).containsEntry("status", "ACTIVE");
        verify(schedules).saveAndFlush(argThat(ReportSchedule::isNew));
    }

    @Test
    @DisplayName("Without the definition's required_action: 403 - the check that stands for every later run")
    void definitionsActionIsRequired() {
        doThrow(new PermissionDeniedException("core.employee.export"))
                .when(permissions)
                .require("core.employee.export");

        assertThatThrownBy(() -> service.put(UUID.randomUUID(), request("DAILY", null, null, "a@b.test")))
                .isInstanceOf(PermissionDeniedException.class);
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A day that cannot occur under the cadence is refused")
    void dayMustFitTheCadence() {
        assertThat(errorsFor(request("WEEKLY", 15, null, "a@b.test"))).containsKey("dayOfPeriod");
        assertThat(errorsFor(request("WEEKLY", null, null, "a@b.test"))).containsKey("dayOfPeriod");
        assertThat(errorsFor(request("MONTHLY", 32, null, "a@b.test"))).containsKey("dayOfPeriod");
        assertThat(errorsFor(request("HOURLY", null, null, "a@b.test"))).containsKey("cadence");
    }

    @Test
    @DisplayName("Recipients: at least one, real addresses, at most the limit")
    void recipientsAreChecked() {
        assertThat(errorsFor(request("DAILY", null, null, "  "))).containsKey("recipientEmails");
        assertThat(errorsFor(request("DAILY", null, null, "a@b.test, not-an-address")))
                .extractingByKey("recipientEmails")
                .asString()
                .contains("not-an-address");
        String tooMany = String.join(
                ",",
                IntStream.rangeClosed(0, ReportScheduleService.MAX_RECIPIENTS)
                        .mapToObj(i -> "r" + i + "@b.test")
                        .toList());
        assertThat(errorsFor(request("DAILY", null, null, tooMany))).containsKey("recipientEmails");
    }

    @Test
    @DisplayName("A filter the source does not declare is refused when saved, not at 02:00 when run")
    void unknownFilterIsRefused() {
        assertThat(errorsFor(request("DAILY", null, Map.of("department", "x"), "a@b.test")))
                .extractingByKey("filters")
                .asString()
                .contains("department");
    }

    @Test
    @DisplayName("A definition in no tenant of the caller's is a 400 on the body, and nothing is checked further")
    void unknownDefinitionIsRefused() {
        ReportScheduleService.ReportScheduleRequest other = new ReportScheduleService.ReportScheduleRequest(
                UUID.randomUUID(), "DAILY", null, NINE, null, "a@b.test", true);

        assertThat(errorsFor(other)).containsKey("definitionId");
        verify(permissions, never()).require(any());
    }

    @Test
    @DisplayName("PUT on an existing schedule replaces its settings and keeps its run state")
    void replacesAnExistingSchedule() {
        UUID id = UUID.randomUUID();
        ReportSchedule existing =
                new ReportSchedule(TENANT, definitionId, "DAILY", null, NINE, null, "old@b.test", true, "test");
        existing.setId(id);
        existing.recordRun(Instant.parse("2026-09-27T09:00:00Z"), ReportSchedule.STATUS_SUCCESS, null, "worker");
        existing.markNotNew();
        when(schedules.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(existing));

        ReportScheduleService.ReportScheduleResponse saved =
                service.put(id, request("MONTHLY", 31, null, "new@b.test"));

        assertThat(saved.cadence()).isEqualTo("MONTHLY");
        assertThat(saved.dayOfPeriod()).isEqualTo(31);
        assertThat(saved.recipients()).containsExactly("new@b.test");
        assertThat(saved.lastRunStatus()).isEqualTo(ReportSchedule.STATUS_SUCCESS);
        assertThat(existing.isNew()).isFalse();
    }

    @Test
    @DisplayName("The stored recipient list splits on commas, semicolons and white space")
    void recipientsOfSplits() {
        assertThat(ReportScheduleServiceImpl.recipientsOf("a@b.test, c@d.test;e@f.test\ng@h.test"))
                .containsExactly("a@b.test", "c@d.test", "e@f.test", "g@h.test");
        assertThat(ReportScheduleServiceImpl.recipientsOf(null)).isEmpty();
    }

    private ReportScheduleService.ReportScheduleRequest request(
            String cadence, Integer day, Map<String, String> filters, String recipients) {
        return new ReportScheduleService.ReportScheduleRequest(
                definitionId, cadence, day, NINE, filters, recipients, true);
    }

    private Map<String, String> errorsFor(ReportScheduleService.ReportScheduleRequest request) {
        try {
            service.put(UUID.randomUUID(), request);
        } catch (ReportDefinitionService.ValidationException e) {
            return e.fieldErrors();
        }
        throw new AssertionError("expected the request to be refused");
    }
}
