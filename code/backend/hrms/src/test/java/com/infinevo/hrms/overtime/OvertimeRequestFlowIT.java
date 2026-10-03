package com.infinevo.hrms.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.approval.OutcomeDispatcher;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-40.6 §7, {@code OvertimeRequestFlowIT}: the employee submits 3 hours, the row is {@code PENDING} with no ledger
 * row and two pending steps (manager, HR). One approval changes nothing; the other turns the row {@code APPROVED} and
 * posts exactly one {@code OVERTIME} row of quantity 3.00. A second dispatch of the instance leaves one.
 */
class OvertimeRequestFlowIT extends OvertimeRequestItSupport {

    @Autowired
    private OutcomeDispatcher outcomeDispatcher;

    @Test
    @DisplayName("Submit 3 hours, manager and HR approve, one OVERTIME ledger row of 3.00; a second dispatch adds none")
    void approvedRequestReachesTheLedger() throws Exception {
        JsonNode submitted = submitAs(employeeId, employeeSub, "3");
        assertThat(submitted.get("status").asText()).isEqualTo("PENDING");
        assertThat(submitted.get("source").asText()).isEqualTo("REQUEST");
        assertThat(submitted.get("employee_id").asText()).isEqualTo(employeeId.toString());
        UUID requestId = UUID.fromString(submitted.get("id").asText());

        assertThat(statusOf(requestId)).isEqualTo("PENDING");
        assertThat(ledgerRows(requestId)).isEmpty();

        // Two pending steps: the reporting manager and HR.
        UUID instanceId = instanceOf(requestId);
        List<ApprovalStep> pending = stepsOf(instanceId);
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(s -> s.getDecision() == null);
        assertThat(pending)
                .extracting(ApprovalStep::getApproverKind)
                .containsExactlyInAnyOrder(ApproverKind.REPORTING_MANAGER, ApproverKind.ROLE);
        assertThat(pending).extracting(ApprovalStep::getAssigneeEmployeeId).containsExactlyInAnyOrder(managerId, hrId);

        // HR first: the order does not matter, and one approval is not enough.
        decide(stepFor(instanceId, hrId).getId(), hrId, hrSub, "APPROVED");
        assertThat(statusOf(requestId)).isEqualTo("PENDING");
        assertThat(ledgerRows(requestId)).isEmpty();

        decide(stepFor(instanceId, managerId).getId(), managerId, managerSub, "APPROVED");
        assertThat(statusOf(requestId)).isEqualTo("APPROVED");
        List<OvertimeRequestItSupport.LedgerRow> rows = ledgerRows(requestId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).kind()).isEqualTo("OVERTIME");
        assertThat(rows.get(0).quantity()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(rows.get(0).amount()).isNull();
        assertThat(rows.get(0).employeeId()).isEqualTo(employeeId);

        // A second dispatch: the claim is taken, so nothing runs. Releasing the claim runs the handler again, and
        // OvertimeService.approve returns quietly for an APPROVED row.
        TenantContext.set(tenant);
        try {
            assertThat(outcomeDispatcher.dispatch(instanceId)).isFalse();
        } finally {
            TenantContext.clear();
        }
        HrmsProjectTestSchema.update(
                "UPDATE core.approval_instance SET outcome_notified_at = NULL WHERE id = ?", instanceId);
        TenantContext.set(tenant);
        try {
            assertThat(outcomeDispatcher.dispatch(instanceId)).isTrue();
        } finally {
            TenantContext.clear();
        }
        assertThat(ledgerRows(requestId)).hasSize(1);
        assertThat(ledgerCount()).isEqualTo(1);
        assertThat(statusOf(requestId)).isEqualTo("APPROVED");

        // The employee reads it as theirs.
        mvc.perform(authed(get(BASE + "/mine?from=" + DAY.minusDays(5) + "&to=" + DAY.plusDays(1)), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("No active OVERTIME definition: the submit is 409 and the row is rolled back")
    void noActiveDefinitionRollsBack() throws Exception {
        HrmsProjectTestSchema.update(
                "UPDATE core.approval_definition SET is_active = false WHERE tenant_id = ? AND flow_type = 'OVERTIME'",
                tenant);
        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY, "2", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("Hours over 24, zero hours and a future date are 400 VALIDATION_FAILED; nothing is saved")
    void invalidInputIsValidationFailed() throws Exception {
        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY, "25", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY, "0", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY.plusDays(30), "2", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(requestCount()).isZero();
    }
}
