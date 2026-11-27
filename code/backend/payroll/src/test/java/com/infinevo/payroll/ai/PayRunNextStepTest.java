package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.payrun.PayRunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** W-114.21 spec, Tests: {@link PayRunNextStep} - one step per status, and the action each needs. */
class PayRunNextStepTest {

    @ParameterizedTest
    @EnumSource(PayRunStatus.class)
    @DisplayName("every status has a step (a new status fails here until it gets one)")
    void everyStatusHasAStep(PayRunStatus status) {
        PayRunNextStep.Step step = PayRunNextStep.of(status);

        assertThat(step).isNotNull();
        assertThat(step.nextStep()).isNotBlank();
    }

    @Test
    @DisplayName("each status's step and action follow the endpoints")
    void table() {
        assertThat(PayRunNextStep.of(null).requiredAction()).isEqualTo("payroll.run.execute");
        assertThat(PayRunNextStep.of(PayRunStatus.DRAFT).nextStep()).contains("lock the run");
        assertThat(PayRunNextStep.of(PayRunStatus.LOCKED).requiredAction()).isEqualTo("payroll.run.execute");
        assertThat(PayRunNextStep.of(PayRunStatus.COMPUTING).requiredAction()).isNull();
        assertThat(PayRunNextStep.of(PayRunStatus.FAILED).requiredAction()).isEqualTo("payroll.run.execute");
        assertThat(PayRunNextStep.of(PayRunStatus.COMPUTED).nextStep()).startsWith("Review the figures");
        assertThat(PayRunNextStep.of(PayRunStatus.COMPUTED).requiredAction()).isEqualTo("payroll.run.approve");
        assertThat(PayRunNextStep.of(PayRunStatus.APPROVED).requiredAction()).isEqualTo("payroll.payslip.publish");
        assertThat(PayRunNextStep.of(PayRunStatus.PAID).requiredAction()).isNull();
        assertThat(PayRunNextStep.of(PayRunStatus.CANCELLED).requiredAction()).isNull();
    }
}
