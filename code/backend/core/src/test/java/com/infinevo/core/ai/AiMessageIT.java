package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.4 spec, Verification: {@code POST /api/v1/ai/sessions/{id}/messages} through the shipped filter chain, the
 * real permission check, the real {@link CoreAiToolProvider} and PostgreSQL with row-level security. The model is the
 * {@link StubAiModelClient} the app picks with no endpoint set, scripted per test. Built like {@code AiSessionIT}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
@ExtendWith(OutputCaptureExtension.class)
class AiMessageIT extends AbstractIntegrationTest {

    private static final String PAN = "ABCDE1234F";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subA;
    private UUID subB;
    private UUID sessionA;

    @BeforeEach
    void setUp() throws SQLException {
        assertThat(modelClient).isInstanceOf(StubAiModelClient.class);
        stub = (StubAiModelClient) modelClient;
        stub.reset();

        tenant = AuthzTestSchema.insertTenant("AiMessageCo " + UUID.randomUUID());
        subscription(tenant, true, 100);
        UUID role = AuthzTestSchema.insertRole(
                tenant,
                "ai-reader-" + UUID.randomUUID().toString().substring(0, 8),
                "AI reader",
                "core.ai.use",
                "core.leave.read_own",
                "core.org.read");

        subA = UUID.randomUUID();
        UUID accountA = member(tenant, subA, role);
        UUID employeeA =
                AuthzTestSchema.insertEmployee(tenant, "E-A-" + subA.toString().substring(0, 6), "Asha");
        AuthzTestSchema.linkEmployee(employeeA, accountA);

        subB = UUID.randomUUID();
        UUID accountB = member(tenant, subB, role);
        UUID employeeB =
                AuthzTestSchema.insertEmployee(tenant, "E-B-" + subB.toString().substring(0, 6), "Bala");
        AuthzTestSchema.linkEmployee(employeeB, accountB);

        sessionA = AiChatRows.session(tenant, subA);
    }

    @Test
    @DisplayName("stub client: the app started with no AI endpoint and answers 'not connected' when not scripted")
    void stubClient_answersNotConnected() throws Exception {
        ask(subA, sessionA, "Hello")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.content").value(StubAiModelClient.NOT_CONNECTED));
    }

    @Test
    @DisplayName("helper off: 403 AI_DISABLED, no message row, usage count unchanged")
    void helperOff_forbidden() throws Exception {
        UUID offTenant = AuthzTestSchema.insertTenant("AiMessageOff " + UUID.randomUUID());
        subscription(offTenant, false, null);
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(offTenant, sub, "aimsg-" + sub + "@aimsg.test");
        AuthzTestSchema.grant(
                offTenant,
                account,
                AuthzTestSchema.insertRole(offTenant, "ai-off-" + sub.toString().substring(0, 8), "AI", "core.ai.use"));
        UUID session = AiChatRows.session(offTenant, sub);

        ask(offTenant, sub, session, "How many leave days do I have?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));

        assertThat(AiChatRows.countForSession("ai_message", session)).isZero();
        assertThat(usage(offTenant)).isZero();
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    @DisplayName("one tool round: 200; USER, TOOL, ASSISTANT stored; usage + 1; the tool result reached the model")
    void toolRound_threeRows() throws Exception {
        stub.script(
                AiModelResponse.toolCall("call_1", "core.my_leave_balances", "{}"),
                AiModelResponse.text("You have no leave balances yet."));

        ask(subA, sessionA, "How many leave days do I have left?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.message.content").value("You have no leave balances yet."))
                .andExpect(jsonPath("$.proposedAction").isEmpty());

        assertThat(roles(sessionA)).containsExactly("USER", "TOOL", "ASSISTANT");
        assertThat(usage(tenant)).isEqualTo(1);
        List<AiModelRequest> sent = stub.requests();
        assertThat(sent).hasSize(2);
        assertThat(toolNames(sent.get(0))).contains("core.my_leave_balances", "core.org_lookup");
        AiModelRequest.Message toolMessage = last(sent.get(1));
        assertThat(toolMessage.role()).isEqualTo(AiModelRequest.Role.TOOL);
        assertThat(toolMessage.toolCallId()).isEqualTo("call_1");
        assertThat(toolMessage.content()).isEqualTo(contents(sessionA).get(1));
    }

    @Test
    @DisplayName("a login without core.leave.read_own: core.my_leave_balances is not in the list sent to the model")
    void withoutLeaveReadOwn_toolNotOffered() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID account = member(
                tenant,
                sub,
                AuthzTestSchema.insertRole(
                        tenant, "ai-only-" + sub.toString().substring(0, 8), "AI only", "core.ai.use"));
        UUID employee =
                AuthzTestSchema.insertEmployee(tenant, "E-C-" + sub.toString().substring(0, 6), "Chitra");
        AuthzTestSchema.linkEmployee(employee, account);
        UUID session = AiChatRows.session(tenant, sub);
        stub.script(AiModelResponse.text("I can't see that."));

        ask(sub, session, "How many leave days do I have left?").andExpect(status().isOk());

        assertThat(stub.requests()).hasSize(1);
        assertThat(toolNames(stub.requests().get(0)))
                .doesNotContain("core.my_leave_balances", "core.my_leave_requests", "core.org_lookup");
    }

    @Test
    @DisplayName("a PAN in the question: the stored USER row and the text sent to the model read [PAN]")
    void pan_maskedStoredAndSent() throws Exception {
        stub.script(AiModelResponse.text("Noted."));

        ask(subA, sessionA, "My PAN is " + PAN).andExpect(status().isOk());

        assertThat(contents(sessionA).get(0)).isEqualTo("My PAN is [PAN]");
        AiModelRequest.Message sentQuestion = last(stub.requests().get(0));
        assertThat(sentQuestion.role()).isEqualTo(AiModelRequest.Role.USER);
        assertThat(sentQuestion.content()).isEqualTo("My PAN is [PAN]");
        assertThat(stub.requests().get(0).toString()).doesNotContain(PAN);
    }

    @Test
    @DisplayName("posting to another user's session: 404; nothing stored, nothing counted, no model call")
    void otherUsersSession_notFound() throws Exception {
        ask(subB, sessionA, "What is in Asha's chat?")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(AiChatRows.countForSession("ai_message", sessionA)).isZero();
        assertThat(usage(tenant)).isZero();
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    @DisplayName("model fails: 502 INTERNAL with the fixed message; the USER row stays; usage + 1")
    void modelFails_badGateway() throws Exception {
        stub.scriptFailure();

        ask(subA, sessionA, "How many leave days do I have left?")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value(AiMessageController.MODEL_FAILED));

        assertThat(roles(sessionA)).containsExactly("USER");
        assertThat(usage(tenant)).isEqualTo(1);
    }

    @Test
    @DisplayName("content filter stop: 200 with the fixed line; ASSISTANT row stored; usage + 1")
    void contentFilter_fixedLine() throws Exception {
        stub.script(AiModelResponse.filtered());

        ask(subA, sessionA, "Something the filter stops")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.content").value(AiChatServiceImpl.FILTERED_ANSWER));

        assertThat(roles(sessionA)).containsExactly("USER", "ASSISTANT");
        assertThat(contents(sessionA).get(1)).isEqualTo(AiChatServiceImpl.FILTERED_ANSWER);
        assertThat(usage(tenant)).isEqualTo(1);
    }

    @Test
    @DisplayName("second question after a tool round: no tool message from the first question is sent again")
    void secondQuestion_noEarlierToolMessage() throws Exception {
        stub.script(
                AiModelResponse.toolCall("call_1", "core.my_leave_balances", "{}"),
                AiModelResponse.text("None yet."),
                AiModelResponse.text("You're welcome."));

        ask(subA, sessionA, "How many leave days do I have left?").andExpect(status().isOk());
        ask(subA, sessionA, "Thanks").andExpect(status().isOk());

        List<AiModelRequest> sent = stub.requests();
        assertThat(sent).hasSize(3);
        AiModelRequest second = sent.get(2);
        assertThat(second.messages())
                .extracting(AiModelRequest.Message::role)
                .containsExactly(AiModelRequest.Role.USER, AiModelRequest.Role.ASSISTANT, AiModelRequest.Role.USER);
        assertThat(second.messages()).allSatisfy(m -> assertThat(m.toolCalls()).isNullOrEmpty());
        assertThat(roles(sessionA)).containsExactly("USER", "TOOL", "ASSISTANT", "USER", "ASSISTANT");
        assertThat(usage(tenant)).isEqualTo(2);
    }

    @Test
    @DisplayName("customer at its cap: 429 AI_CAP_REACHED; nothing stored for the refused question")
    void atCap_tooManyRequests() throws Exception {
        UUID capped = AuthzTestSchema.insertTenant("AiMessageCap " + UUID.randomUUID());
        subscription(capped, true, 1);
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(capped, sub, "aicap-" + sub + "@aimsg.test");
        AuthzTestSchema.grant(
                capped,
                account,
                AuthzTestSchema.insertRole(capped, "ai-cap-" + sub.toString().substring(0, 8), "AI", "core.ai.use"));
        UUID session = AiChatRows.session(capped, sub);

        ask(capped, sub, session, "First").andExpect(status().isOk());
        ask(capped, sub, session, "Second")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_CAP_REACHED"));

        assertThat(roles(session)).containsExactly("USER", "ASSISTANT");
        assertThat(usage(capped)).isEqualTo(1);
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    @DisplayName("six tool rounds asked: 200 with the fixed text; five TOOL rows; six model calls")
    void sixToolRounds_stops() throws Exception {
        for (int i = 1; i <= 6; i++) {
            stub.script(AiModelResponse.toolCall("call_" + i, "core.my_leave_balances", "{}"));
        }

        ask(subA, sessionA, "Keep checking my balance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.content").value(AiChatServiceImpl.TOO_MANY_ROUNDS));

        assertThat(roles(sessionA)).containsExactly("USER", "TOOL", "TOOL", "TOOL", "TOOL", "TOOL", "ASSISTANT");
        assertThat(stub.requests()).hasSize(6);
        assertThat(usage(tenant)).isEqualTo(1);
    }

    @Test
    @DisplayName("the log holds no PAN, no question, no tool result and no answer text - only counts and names")
    void log_holdsNoText(CapturedOutput output) throws Exception {
        String department = "Quokkadept" + UUID.randomUUID().toString().substring(0, 6);
        department(tenant, "QK", department);
        String answer = "Zebra answer " + UUID.randomUUID();
        stub.script(
                AiModelResponse.toolCall("call_1", "core.org_lookup", "{\"kind\":\"DEPARTMENT\"}"),
                AiModelResponse.text(answer));
        String question = "Which departments exist? My PAN is " + PAN;

        ask(subA, sessionA, question)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.content").value(answer));

        String toolRow = contents(sessionA).get(1);
        assertThat(toolRow).contains(department).doesNotContain(tenant.toString());
        assertThat(output.getAll())
                .contains("AI message answered")
                .contains("core.org_lookup")
                .doesNotContain(PAN)
                .doesNotContain("[PAN]")
                .doesNotContain("Which departments exist")
                .doesNotContain(department)
                .doesNotContain(toolRow)
                .doesNotContain(answer);
    }

    @Test
    @DisplayName("a tool result sent to the model and stored carries no database id")
    void toolResult_noIds() throws Exception {
        department(tenant, "ENG", "Engineering");
        stub.script(
                AiModelResponse.toolCall("call_1", "core.org_lookup", "{\"kind\":\"DEPARTMENT\"}"),
                AiModelResponse.text("Engineering."));

        ask(subA, sessionA, "Which departments exist?").andExpect(status().isOk());

        String toolRow = contents(sessionA).get(1);
        assertThat(toolRow).contains("Engineering").contains("ENG");
        assertThat(toolRow)
                .doesNotContainPattern("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                .doesNotContain("\"id\"")
                .doesNotContain("tenantId");
        assertThat(last(stub.requests().get(1)).content()).isEqualTo(toolRow);
    }

    // ---- helpers ----

    private ResultActions ask(UUID sub, UUID session, String content) throws Exception {
        return ask(tenant, sub, session, content);
    }

    private ResultActions ask(UUID tenantId, UUID sub, UUID session, String content) throws Exception {
        return mvc.perform(as(
                tenantId,
                sub,
                post("/api/v1/ai/sessions/{id}/messages", session)
                        .content(json.writeValueAsString(Map.of("content", content)))));
    }

    private static List<String> toolNames(AiModelRequest request) {
        return request.tools().stream().map(AiToolDescriptor::name).toList();
    }

    private static AiModelRequest.Message last(AiModelRequest request) {
        return request.messages().get(request.messages().size() - 1);
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aimsg-" + sub + "@aimsg.test");
        AuthzTestSchema.grant(tenantId, account, role);
        return account;
    }

    private static void subscription(UUID tenantId, boolean aiOn, Integer cap) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled,"
                                + " ai_monthly_request_cap) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setBoolean(3, aiOn);
            ps.setObject(4, cap, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    private static void department(UUID tenantId, String code, String name) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.department (tenant_id, code, name) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    /** This month's {@code request_count} for the tenant, read as the schema owner; 0 with no row. */
    private static int usage(UUID tenantId) throws SQLException {
        LocalDate month = YearMonth.now(ZoneOffset.UTC).atDay(1);
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT request_count FROM core.ai_usage WHERE tenant_id = ? AND usage_month = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static List<String> roles(UUID sessionId) throws SQLException {
        return column(sessionId, "role");
    }

    private static List<String> contents(UUID sessionId) throws SQLException {
        return column(sessionId, "content");
    }

    /** One column of the session's messages, oldest first, read as the schema owner. */
    private static List<String> column(UUID sessionId, String column) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT " + column + " FROM core.ai_message WHERE session_id = ? ORDER BY created_at, ctid")) {
            ps.setObject(1, sessionId);
            List<String> values = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
            return values;
        }
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
