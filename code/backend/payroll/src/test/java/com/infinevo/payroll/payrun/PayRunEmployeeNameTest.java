package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * D-82 — a run's employee rows carry the employee's name, read through
 * {@code EmployeeService.displayNames} for the page's own ids, so a role that reads pay runs but not
 * the employee directory (finance) still sees who each row is.
 */
class PayRunEmployeeNameTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private EmployeePayRunRepository employeePayRuns;
    private EmployeeService employeeService;
    private PayRunServiceImpl service;
    private PayRun run;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PayRunRepository payRuns = mock(PayRunRepository.class);
        employeePayRuns = mock(EmployeePayRunRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new PayRunServiceImpl(
                payRuns,
                employeePayRuns,
                mock(PayPeriodService.class),
                employeeService,
                mock(PayRunInclusionService.class),
                mock(PayInputService.class),
                mock(EmployeePayRunLineRepository.class),
                mock(JobService.class),
                (ObjectProvider<QueueProducer>) mock(ObjectProvider.class),
                mock(PlatformTransactionManager.class));
        run = new PayRun(
                TENANT,
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 31),
                2,
                0,
                "officer");
        when(payRuns.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(employeeService.listEmployedBetween(any(), any())).thenReturn(List.of());
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Each row carries the name displayNames gives for its employee; one read for the page")
    void rowsCarryNames() {
        Page<EmployeePayRun> page = new PageImpl<>(List.of(row(ALICE), row(BOB)));
        when(employeePayRuns.findByTenantIdAndPayrunId(eq(TENANT), any(), any()))
                .thenReturn(page);
        when(employeeService.displayNames(Set.of(ALICE, BOB)))
                .thenReturn(Map.of(ALICE, "Alice Smith", BOB, "Bob Jones"));

        List<EmployeePayRunResponse> rows =
                service.employees(RUN, null, PageRequest.of(0, 10)).getContent();

        assertThat(rows).extracting(EmployeePayRunResponse::employeeName).containsExactly("Alice Smith", "Bob Jones");
        verify(employeeService).displayNames(Set.of(ALICE, BOB));
    }

    @Test
    @DisplayName("An empty page asks for no names")
    void emptyPageAsksNothing() {
        when(employeePayRuns.findByTenantIdAndPayrunId(eq(TENANT), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.employees(RUN, null, PageRequest.of(0, 10)).getContent())
                .isEmpty();
        verify(employeeService, never()).displayNames(any());
    }

    private static EmployeePayRun row(UUID employeeId) {
        return new EmployeePayRun(TENANT, RUN, InclusionDecision.included(employeeId, UUID.randomUUID()), "officer");
    }
}
