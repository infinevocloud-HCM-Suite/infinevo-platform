package com.infinevo.hrms.overtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalStep;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-40.6 §7, {@code OvertimeRequestRejectIT}: one approver rejects, the row is {@code REJECTED}, no ledger row is
 * written, and the other step is closed.
 */
class OvertimeRequestRejectIT extends OvertimeRequestItSupport {

    @Test
    @DisplayName("The manager rejects: the row is REJECTED, no ledger row, and HR's step is closed")
    void rejectedRequestPostsNothing() throws Exception {
        JsonNode submitted = submitAs(employeeId, employeeSub, "3");
        UUID requestId = UUID.fromString(submitted.get("id").asText());
        UUID instanceId = instanceOf(requestId);

        decide(stepFor(instanceId, managerId).getId(), managerId, managerSub, "REJECTED");

        assertThat(statusOf(requestId)).isEqualTo("REJECTED");
        assertThat(ledgerRows(requestId)).isEmpty();
        assertThat(ledgerCount()).isZero();

        ApprovalStep hrStep = stepFor(instanceId, hrId);
        assertThat(hrStep.getDecision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(hrStep.getDecidedAt()).isNotNull();
        assertThat(stepsOf(instanceId)).allMatch(s -> s.getDecision() != null);
    }
}
