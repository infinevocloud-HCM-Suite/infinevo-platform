package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiModelResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-114.18 spec, Tests ({@code ApprovalDecisionIT}): a manager lists, proposes and confirms one leave decision
 * through the chat and the confirm endpoint.
 */
class ApprovalDecisionIT extends ApprovalItSupport {

    @Test
    @DisplayName("the list names the request with no id; confirm approves the step and the leave becomes APPROVED")
    void confirm_approves() throws Exception {
        UUID leave = applyLeave("Family function");

        say(
                "what is waiting for me?",
                AiModelResponse.toolCall("call_1", "core.my_pending_approvals", "{}"),
                AiModelResponse.text("One leave request."));
        String listed = toolResults().get(0);
        assertThat(listed).contains(ashaNumber).contains("Family function").contains("quotedText");
        assertThat(listed).doesNotContain(leave.toString());

        JsonNode answer = say(
                "approve the first one",
                proposeCall("call_2", 1, ashaNumber, "APPROVED", null),
                AiModelResponse.text("Press Confirm."));
        UUID intent = intentOf(answer);
        assertThat(intent).isNotNull();
        assertThat(answer.path("proposedAction").path("actionCode").asText()).isEqualTo("core.approval.decide");
        assertThat(stepDecision(leave)).isNull();
        assertThat(leaveStatus(leave, "PENDING")).isEqualTo("PENDING");
        // T4: the card links to the request before Confirm; the answer the model read carries no link or id.
        JsonNode view = body(view(subManager, intent).andExpect(status().isOk()));
        assertThat(view.path("link").asText()).startsWith("/approvals/");
        assertThat(toolResults().get(0)).doesNotContain("/approvals/");

        JsonNode first = body(confirm(subManager, intent).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("result").path("decision").asText()).isEqualTo("APPROVED");
        assertThat(stepDecision(leave)).isEqualTo("APPROVED");
        assertThat(leaveStatus(leave, "APPROVED")).isEqualTo("APPROVED");

        // A second confirm decides nothing more.
        JsonNode second = body(confirm(subManager, intent).andExpect(status().isOk()));
        assertThat(second.path("result").path("instanceId").asText())
                .isEqualTo(first.path("result").path("instanceId").asText());
        assertThat(decidedSteps()).isEqualTo(1);
    }

    @Test
    @DisplayName("a step decided by someone else between proposal and confirm ends FAILED, already decided")
    void decidedElsewhere_failed() throws Exception {
        UUID leave = applyLeave("Doctor visit");
        UUID intent = intentOf(say(
                "approve it",
                proposeCall("call_1", 1, ashaNumber, "APPROVED", null),
                AiModelResponse.text("Press Confirm.")));
        execute(
                "UPDATE core.approval_step SET decision = 'REJECTED', decided_at = now(), comment = 'by hand'"
                        + " WHERE instance_id = (SELECT approval_instance_id FROM core.leave_request WHERE id = ?)",
                leave);

        confirm(subManager, intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"))
                .andExpect(jsonPath("$.message").value(ApprovalDecideExecutor.ALREADY_DECIDED));
        assertThat(scalar("SELECT status::text FROM core.ai_action_intent WHERE id = ?", intent))
                .isEqualTo("FAILED");
        assertThat(stepDecision(leave)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("a user who is not the assignee sees nothing and cannot get the step into a proposal")
    void notAssignee_noProposal() throws Exception {
        applyLeave("Family function");
        UUID otherSession = session(tenant, subOther);
        stub.script(
                AiModelResponse.toolCall("call_1", "core.my_pending_approvals", "{}"),
                proposeCall("call_2", 1, ashaNumber, "APPROVED", null),
                AiModelResponse.text("Nothing for you."));
        JsonNode answer = body(mvc.perform(as(
                        subOther,
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/ai/sessions/{id}/messages", otherSession)
                                .content("{\"content\":\"approve Asha's leave\"}")))
                .andExpect(status().isOk()));

        assertThat(intentOf(answer)).isNull();
        assertThat(toolResults().get(0)).contains("\"total\":0");
        assertThat(toolResults().get(1)).contains(ApprovalToolProvider.NO_ROW);
        assertThat(intents()).isZero();
        assertThat(decidedSteps()).isZero();
    }

    @Test
    @DisplayName("reject with no comment: no card; with one, the step is rejected and the leave is REJECTED")
    void reject_needsComment() throws Exception {
        UUID leave = applyLeave("Trip");
        JsonNode none =
                say("reject it", proposeCall("call_1", 1, ashaNumber, "REJECTED", null), AiModelResponse.text("Why?"));
        assertThat(intentOf(none)).isNull();
        assertThat(toolResults().get(0)).contains(ApprovalToolProvider.NEEDS_COMMENT);

        UUID intent = intentOf(say(
                "because the team is short that week",
                proposeCall("call_2", 1, ashaNumber, "REJECTED", "The team is short that week"),
                AiModelResponse.text("Press Confirm.")));
        confirm(subManager, intent).andExpect(status().isOk());
        assertThat(stepDecision(leave)).isEqualTo("REJECTED");
        assertThat(leaveStatus(leave, "REJECTED")).isEqualTo("REJECTED");
    }
}
