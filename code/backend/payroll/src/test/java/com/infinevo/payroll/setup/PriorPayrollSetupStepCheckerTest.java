package com.infinevo.payroll.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.priorpayroll.PriorPayrollMonthRepository;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * W-38.3 §7: Unit tests for prior payroll setup step checker.
 */
class PriorPayrollSetupStepCheckerTest {

    private final UUID tenantId = UUID.randomUUID();
    private PriorPayrollMonthRepository months;
    private PayRunRepository runs;
    private SetupStepChecker checker;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @BeforeEach
    void setUp() {
        months = mock(PriorPayrollMonthRepository.class);
        runs = mock(PayRunRepository.class);
        checker = new PayrollSetupStepConfiguration().priorPayrollSetupStepChecker(provider(months), provider(runs));
    }

    @Test
    @DisplayName("an imported row marks PRIOR_PAYROLL done, runs are not queried")
    void anImportedRowMarksStepDone() {
        when(months.existsByTenantId(tenantId)).thenReturn(true);

        assertThat(checker.isComplete(tenantId)).isTrue();
        verifyNoInteractions(runs);
    }

    @Test
    @DisplayName("no import, but first regular non-cancelled run is in April marks PRIOR_PAYROLL done")
    void noImportFirstRunInAprilMarksStepDone() {
        when(months.existsByTenantId(tenantId)).thenReturn(false);
        PayRun run = mock(PayRun.class);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 4));
        when(runs.findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
                        tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED))
                .thenReturn(Optional.of(run));

        assertThat(checker.isComplete(tenantId)).isTrue();
    }

    @Test
    @DisplayName("no import and first regular non-cancelled run is in October marks PRIOR_PAYROLL not done")
    void firstRunInOctoberMarksStepNotDone() {
        when(months.existsByTenantId(tenantId)).thenReturn(false);
        PayRun run = mock(PayRun.class);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 10));
        when(runs.findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
                        tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED))
                .thenReturn(Optional.of(run));

        assertThat(checker.isComplete(tenantId)).isFalse();
    }

    @Test
    @DisplayName("first run in April was cancelled and next is May marks PRIOR_PAYROLL not done")
    void firstRunCancelledNextInMayMarksStepNotDone() {
        when(months.existsByTenantId(tenantId)).thenReturn(false);
        PayRun run = mock(PayRun.class);
        when(run.getPeriod()).thenReturn(YearMonth.of(2026, 5));
        when(runs.findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
                        tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED))
                .thenReturn(Optional.of(run));

        assertThat(checker.isComplete(tenantId)).isFalse();
    }

    @Test
    @DisplayName("no run and no import marks PRIOR_PAYROLL not done")
    void noRunNoImportMarksStepNotDone() {
        when(months.existsByTenantId(tenantId)).thenReturn(false);
        when(runs.findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
                        tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED))
                .thenReturn(Optional.empty());

        assertThat(checker.isComplete(tenantId)).isFalse();
    }

    @Test
    @DisplayName("null tenant reports false")
    void nullTenantReportsFalse() {
        assertThat(checker.isComplete(null)).isFalse();
    }

    @Test
    @DisplayName("both providers returning null reports false")
    void bothProvidersEmptyReportsFalse() {
        SetupStepChecker emptyChecker =
                new PayrollSetupStepConfiguration().priorPayrollSetupStepChecker(provider(null), provider(null));
        assertThat(emptyChecker.isComplete(tenantId)).isFalse();
    }
}
