package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.core.user.ActorNameService;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * D-79: a pay run reply names who locked, cancelled, approved and paid it ({@code *_by_name}), beside the
 * stored Keycloak subject; a list reads every run's actors in one {@code ActorNameService} call.
 */
class PayRunActorNamesTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String OFFICER = UUID.randomUUID().toString();
    private static final String FINANCE = UUID.randomUUID().toString();

    private PayRunRepository payRuns;
    private ActorNameService actorNames;
    private PayRunServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        payRuns = mock(PayRunRepository.class);
        actorNames = mock(ActorNameService.class);
        service = new PayRunServiceImpl(
                payRuns,
                mock(EmployeePayRunRepository.class),
                mock(PayPeriodService.class),
                mock(EmployeeService.class),
                mock(PayRunInclusionService.class),
                mock(PayInputService.class),
                mock(EmployeePayRunLineRepository.class),
                mock(JobService.class),
                (ObjectProvider<QueueProducer>) mock(ObjectProvider.class),
                null,
                null,
                null,
                Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
        service.setActorNameService(actorNames);
        when(actorNames.displayNames(anyCollection()))
                .thenReturn(Map.of(OFFICER, "Priya Nair", FINANCE, "Karan Mehta"));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("get names the approver and the locker; failure_reason stays in the reply")
    void getNamesActors() {
        PayRun run = approvedRun(10);
        when(payRuns.findByIdAndTenantId(run.getId(), TENANT)).thenReturn(Optional.of(run));

        PayRunResponse response = service.get(run.getId());

        assertThat(response.approvedBy()).isEqualTo(FINANCE);
        assertThat(response.approvedByName()).isEqualTo("Karan Mehta");
        assertThat(response.lockedBy()).isEqualTo(OFFICER);
        assertThat(response.lockedByName()).isEqualTo("Priya Nair");
        assertThat(response.paidByName()).isNull();
        assertThat(response.cancelledByName()).isNull();
        assertThat(response.failureReason()).isNull();
    }

    @Test
    @DisplayName("a page of runs reads every actor in one call")
    void listReadsActorsOnce() {
        Pageable pageable = PageRequest.of(0, 20);
        PayRun october = approvedRun(10);
        PayRun november = approvedRun(11);
        when(payRuns.findByTenantId(TENANT, pageable)).thenReturn(new PageImpl<>(List.of(october, november)));

        List<PayRunResponse> rows = service.list(null, null, pageable).getContent();

        assertThat(rows).extracting(PayRunResponse::approvedByName).containsExactly("Karan Mehta", "Karan Mehta");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> actors = ArgumentCaptor.forClass(Collection.class);
        verify(actorNames, times(1)).displayNames(actors.capture());
        assertThat(actors.getValue()).containsExactlyInAnyOrder(OFFICER, FINANCE);
    }

    private static PayRun approvedRun(int month) {
        LocalDate start = LocalDate.of(2026, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        PayRun run = new PayRun(TENANT, YearMonth.of(2026, month), start, end, start.plusDays(24), end, 0, 0, OFFICER);
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.lock(OFFICER, Instant.now());
        run.startComputing(OFFICER);
        run.completeComputation(
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"), 0, OFFICER, Instant.now());
        run.approve(FINANCE, Instant.now());
        return run;
    }
}
