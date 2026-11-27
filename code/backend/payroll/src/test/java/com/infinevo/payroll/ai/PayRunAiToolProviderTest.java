package com.infinevo.payroll.ai;

import static com.infinevo.payroll.ai.PayRunFixtures.row;
import static com.infinevo.payroll.ai.PayRunFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.shared.authz.PermissionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.21 spec, Tests: the two pay run tools over a mocked {@link PayRunService}. Only {@code list} and
 * {@code employees} are ever called on it: the helper takes no pay run step.
 */
class PayRunAiToolProviderTest {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final ObjectMapper json = new ObjectMapper();
    private final AiToolContext ctx = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

    private PayRunService payRuns;
    private PermissionService permissions;
    private Map<String, AiTool> tools;
    private PayRunResponse nov;
    private PayRunResponse oct;

    @BeforeEach
    void setUp() {
        payRuns = mock(PayRunService.class);
        permissions = mock(PermissionService.class);
        TenantClock clock = mock(TenantClock.class);
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 27));
        nov = run("2026-11", PayRunStatus.COMPUTED, "100000", "20000", "80000");
        oct = run("2026-10", PayRunStatus.PAID, "90000", "18000", "72000");
        when(payRuns.list(isNull(), eq(PayRunType.REGULAR), any()))
                .thenReturn(new PageImpl<>(List.of(nov, oct), PageRequest.of(0, 24), 2));
        when(payRuns.list(isNull(), eq(PayRunType.OFF_CYCLE), any())).thenReturn(new PageImpl<>(List.of()));
        UUID asha = UUID.randomUUID();
        List<EmployeePayRunResponse> novRows = List.of(row(asha, "Asha Rao", "E-1", "40000", "1800"));
        List<EmployeePayRunResponse> octRows = List.of(row(asha, "Asha Rao", "E-1", "30000", "1800"));
        when(payRuns.employees(eq(nov.id()), isNull(), any()))
                .thenReturn(new PageImpl<>(novRows, PageRequest.of(0, 200), 1));
        when(payRuns.employees(eq(oct.id()), isNull(), any()))
                .thenReturn(new PageImpl<>(octRows, PageRequest.of(0, 200), 1));
        tools = new PayRunAiToolProvider(payRuns, permissions, clock, TransactionOperations.withoutTransaction())
                .tools().stream().collect(Collectors.toMap(AiTool::name, t -> t));
    }

    private AiToolResult call(String tool, String input) throws Exception {
        return tools.get(tool).execute(ctx, json.readTree(input));
    }

    @Test
    @DisplayName("both tools need payroll.run.read and no employee record")
    void declaration() {
        assertThat(tools.keySet()).containsExactlyInAnyOrder("payroll.payrun_summary", "payroll.payrun_next_step");
        for (AiTool t : tools.values()) {
            assertThat(t.requiredAction()).isEqualTo("payroll.run.read");
            assertThat(t.needsEmployee()).isFalse();
        }
    }

    @Test
    @DisplayName("summary: totals, differences and lists as two-decimal text, with no id; only list and employees read")
    void summary_readsOnly() throws Exception {
        AiToolResult result = call("payroll.payrun_summary", "{\"period\":\"2026-11\"}");

        assertThat(result.ok()).isTrue();
        JsonNode data = result.data();
        assertThat(UUID_TEXT.matcher(data.toString()).find()).isFalse();
        assertThat(data.path("comparedWith").asText()).isEqualTo("2026-10");
        assertThat(data.path("totals").path("thisRun").path("netPay").asText()).isEqualTo("80000.00");
        assertThat(data.path("totals").path("difference").path("netPay").asText())
                .isEqualTo("8000.00");
        assertThat(data.path("largeChange")
                        .path("items")
                        .get(0)
                        .path("difference")
                        .asText())
                .isEqualTo("10000.00");
        assertThat(data.path("screenPath").asText()).isEqualTo("/payroll/runs");

        call("payroll.payrun_next_step", "{\"period\":\"2026-11\"}");
        verify(payRuns, atLeastOnce()).list(any(), any(), any());
        verify(payRuns, atLeastOnce()).employees(any(), any(), any());
        verifyNoMoreInteractions(payRuns);
    }

    @Test
    @DisplayName("a draft run: its status and 'no figures yet'; a month with no run says so")
    void noFigures() throws Exception {
        when(nov.status()).thenReturn(PayRunStatus.DRAFT);

        JsonNode draft =
                call("payroll.payrun_summary", "{\"period\":\"2026-11\"}").data();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("message").asText()).isEqualTo(PayRunAiToolProvider.NO_FIGURES);

        JsonNode none =
                call("payroll.payrun_summary", "{\"period\":\"2026-08\"}").data();
        assertThat(none.path("message").asText()).isEqualTo(PayRunAiToolProvider.NO_RUN);
    }

    @Test
    @DisplayName("next step on a computed run without approve: the step in words, youCanDoThis false, the list path")
    void nextStep_cannotApprove() throws Exception {
        when(permissions.holds("payroll.run.approve")).thenReturn(false);

        JsonNode data = call("payroll.payrun_next_step", "{}").data();

        assertThat(data.path("status").asText()).isEqualTo("COMPUTED");
        assertThat(data.path("nextStep").asText()).startsWith("Review the figures");
        assertThat(data.path("youCanDoThis").asBoolean()).isFalse();
        assertThat(data.path("screenPath").asText()).isEqualTo("/payroll/runs");
    }

    @Test
    @DisplayName("a failed run's reason is never sent, only that there is one")
    void failedReasonHidden() throws Exception {
        when(nov.status()).thenReturn(PayRunStatus.FAILED);
        when(nov.failureReason()).thenReturn("Employee 1234 PAN ABCDE1234F has no salary");

        JsonNode data =
                call("payroll.payrun_next_step", "{\"period\":\"2026-11\"}").data();

        assertThat(data.toString()).doesNotContain("ABCDE1234F").doesNotContain("1234 PAN");
        assertThat(data.path("youCanDoThis").isBoolean()).isTrue();
    }
}
