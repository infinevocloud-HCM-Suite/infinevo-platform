package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.ai.action.AiActionProposals;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.bankfile.BankFileColumnSource;
import com.infinevo.payroll.payrun.bankfile.BankFileField;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateException;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateRequest;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateResponse;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateService;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** W-114.28 step 6, Tests: {@code payroll.propose_bank_file_template} and its executor. */
class BankFileTemplateToolProviderTest {

    private static final String COLUMNS = "\"columns\":[{\"heading\":\"Sr\",\"serial\":true},"
            + "{\"heading\":\"Account\",\"field\":\"account number\"},"
            + "{\"heading\":\"Amount\",\"field\":\"Net pay\",\"format\":\"WHOLE_RUPEES\"},"
            + "{\"heading\":\"Narration\",\"field\":\"narration\"},"
            + "{\"heading\":\"Mode\",\"fixedText\":\"NEFT\"}]";

    private final ObjectMapper json = new ObjectMapper();
    private final List<BankFileTemplateResponse> existing = new ArrayList<>();
    private final AiToolContext ctx = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);
    private AiActionProposals proposals;
    private BankFileTemplateService service;
    private BankFileTemplateCreateExecutor exec;
    private AiTool tool;

    @BeforeEach
    void setUp() {
        service = mock(BankFileTemplateService.class);
        exec = new BankFileTemplateCreateExecutor(service);
        proposals = mock(AiActionProposals.class);
        when(proposals.propose(any(), eq(BankFileTemplateCreateExecutor.ACTION_CODE), any()))
                .thenAnswer(inv -> {
                    JsonNode payload = inv.getArgument(2);
                    var d = exec.describe(ctx, payload);
                    return new AiProposedAction(
                            UUID.randomUUID(),
                            BankFileTemplateCreateExecutor.ACTION_CODE,
                            d.summary(),
                            d.fields(),
                            AiIntentStatus.PROPOSED,
                            Instant.now());
                });
        tool = new BankFileTemplateToolProvider(() -> List.copyOf(existing), proposals)
                .tools()
                .get(0);
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(ctx, json.readTree(input));
    }

    private JsonNode proposedPayload() {
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(proposals).propose(any(), eq(BankFileTemplateCreateExecutor.ACTION_CODE), payload.capture());
        return payload.getValue();
    }

    @Test
    @DisplayName(
            "the tool and the action need payroll.settings.manage, the Bank files tab's add action; payroll module")
    void declaration() {
        assertThat(tool.name()).isEqualTo("payroll.propose_bank_file_template");
        assertThat(tool.requiredAction()).isEqualTo("payroll.settings.manage");
        assertThat(exec.requiredAction()).isEqualTo("payroll.settings.manage");
        assertThat(exec.requiredModule()).isEqualTo(PlatformModule.PAYROLL);
        assertThat(exec.requiresEmployeeProfile()).isFalse();
        assertThat(tool.inputSchema().toString()).contains("Account number").contains("Net pay");
    }

    @Test
    @DisplayName("columns in words become the screen's columns; a card, nothing added; no id")
    void propose() throws Exception {
        AiToolResult result = run("{\"name\":\"HDFC upload\"," + COLUMNS + "}");

        assertThat(result.ok()).isTrue();
        assertThat(result.data().toString())
                .contains("Add bank file template HDFC upload with 5 columns")
                .contains("Account: Account number")
                .contains("Amount: Net pay (WHOLE_RUPEES)")
                .contains("Mode: Fixed text")
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
        BankFileTemplateRequest request = BankFileTemplateCreateExecutor.request(proposedPayload());
        assertThat(request.fileFormat()).isEqualTo(ExportFormat.CSV);
        assertThat(request.includeHeader()).isTrue();
        assertThat(request.columns()).hasSize(5);
        assertThat(request.columns().get(0).source()).isEqualTo(BankFileColumnSource.SERIAL);
        assertThat(request.columns().get(1).field()).isEqualTo(BankFileField.ACCOUNT_NUMBER);
        assertThat(request.columns().get(3).field()).isEqualTo(BankFileField.NARRATION);
        assertThat(request.columns().get(4).value()).isEqualTo("NEFT");
        verify(service, never()).create(any());
    }

    @Test
    @DisplayName(
            "the screen's rules are checked before a card: unknown field, bad format, two sources, a heading twice")
    void refusals() throws Exception {
        assertThat(run("{\"name\":\"T\",\"columns\":[{\"heading\":\"A\",\"field\":\"PAN\"}]}")
                        .error())
                .contains("no field PAN")
                .contains("IFSC");
        assertThat(run("{\"name\":\"T\",\"columns\":[{\"heading\":\"A\",\"field\":\"IFSC\",\"format\":\"PAISE\"}]}")
                        .error())
                .contains("takes no format");
        assertThat(run("{\"name\":\"T\",\"columns\":[{\"heading\":\"A\",\"field\":\"IFSC\",\"serial\":true}]}")
                        .error())
                .contains("exactly one");
        assertThat(
                        run("{\"name\":\"T\",\"columns\":[{\"heading\":\"A\",\"serial\":true},{\"heading\":\"a\",\"serial\":true}]}")
                                .error())
                .contains("used twice");
        assertThat(run("{\"name\":\"T\",\"columns\":[]}").ok()).isFalse();
        verify(proposals, never()).propose(any(), any(), any());
    }

    @Test
    @DisplayName("a name in use is refused; a second proposal in the message is told one step at a time")
    void nameAndOneAtATime() throws Exception {
        BankFileTemplateResponse hdfc = mock(BankFileTemplateResponse.class);
        when(hdfc.name()).thenReturn("HDFC upload");
        existing.add(hdfc);
        assertThat(run("{\"name\":\"hdfc UPLOAD\"," + COLUMNS + "}").error()).contains("already exists");

        when(proposals.propose(any(), any(), any())).thenThrow(new IllegalStateException("already proposed"));
        assertThat(run("{\"name\":\"ICICI\"," + COLUMNS + "}").error()).contains("One step at a time");
    }

    @Test
    @DisplayName("Confirm calls the screen's create; a name taken in between is FAILED")
    void execute() throws Exception {
        assertThat(run("{\"name\":\"HDFC upload\",\"fileFormat\":\"xlsx\",\"includeHeader\":false," + COLUMNS + "}")
                        .ok())
                .isTrue();
        JsonNode payload = proposedPayload();
        BankFileTemplateResponse made = mock(BankFileTemplateResponse.class);
        when(made.id()).thenReturn("t-1");
        when(service.create(any())).thenReturn(made);

        assertThat(exec.execute(ctx, payload).path("templateId").asText()).isEqualTo("t-1");
        ArgumentCaptor<BankFileTemplateRequest> sent = ArgumentCaptor.forClass(BankFileTemplateRequest.class);
        verify(service).create(sent.capture());
        assertThat(sent.getValue().fileFormat()).isEqualTo(ExportFormat.XLSX);
        assertThat(sent.getValue().includeHeader()).isFalse();

        doThrow(new BankFileTemplateException(BankFileTemplateException.Kind.CONFLICT, "name in use"))
                .when(service)
                .create(any());
        assertThatThrownBy(() -> exec.execute(ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("name in use");
        List<AiActionField> fields = exec.describe(ctx, payload).fields();
        assertThat(fields.get(2).value()).isEqualTo("No");
    }
}
