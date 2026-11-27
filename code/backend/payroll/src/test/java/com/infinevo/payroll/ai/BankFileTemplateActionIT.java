package com.infinevo.payroll.ai;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.ai.action.AiActionProposals;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.payrun.bankfile.BankFileColumn;
import com.infinevo.payroll.payrun.bankfile.BankFileColumnSource;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateRequest;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * W-114.28 spec, Tests ({@code BankFileTemplateActionIT}): the bank file template step against the real template
 * service and PostgreSQL with row-level security. The tool is reached through the registry the chat loop calls; the
 * proposal store is a capture, so Confirm here is the executor's {@code execute}, what the confirm endpoint runs.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class BankFileTemplateActionIT extends AbstractIntegrationTest {

    private static final String INPUT = "{\"name\":\"%s\",\"columns\":[{\"heading\":\"Sr\",\"serial\":true},"
            + "{\"heading\":\"Account\",\"field\":\"Account number\"},"
            + "{\"heading\":\"Amount\",\"field\":\"Net pay\",\"format\":\"WHOLE_RUPEES\"}]}";

    @Autowired
    private BankFileTemplateService templates;

    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> proposed = new ArrayList<>();
    private final AiToolContext ctx = new AiToolContext(TENANT_A, UUID.randomUUID(), null);
    private PermissionService permissions;
    private AiToolRegistry registry;
    private BankFileTemplateCreateExecutor exec;
    private String name;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        ctx.userId().toString(), null, List.of()));
        name = "AI upload " + UUID.randomUUID().toString().substring(0, 6);
        exec = new BankFileTemplateCreateExecutor(templates);
        AiActionProposals proposals = mock(AiActionProposals.class);
        when(proposals.propose(any(), eq(BankFileTemplateCreateExecutor.ACTION_CODE), any()))
                .thenAnswer(inv -> {
                    JsonNode payload = inv.getArgument(2);
                    proposed.add(payload);
                    var d = exec.describe(ctx, payload);
                    return new AiProposedAction(
                            UUID.randomUUID(),
                            BankFileTemplateCreateExecutor.ACTION_CODE,
                            d.summary(),
                            d.fields(),
                            AiIntentStatus.PROPOSED,
                            Instant.now());
                });
        permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        registry = new AiToolRegistry(
                permissions, entitlements, List.of(new BankFileTemplateToolProvider(templates, proposals)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private long count(String templateName) {
        return templates.list().stream()
                .filter(t -> templateName.equals(t.name()))
                .count();
    }

    @Test
    @DisplayName("none until Confirm; Confirm makes exactly one, with the columns asked for")
    void confirm_makesOne() throws Exception {
        AiToolResult result =
                registry.execute(BankFileTemplateToolProvider.TOOL, ctx, json.readTree(String.format(INPUT, name)));
        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        assertThat(count(name)).isZero();

        exec.execute(ctx, proposed.get(0));
        assertThat(count(name)).isEqualTo(1);
        var made = templates.list().stream()
                .filter(t -> name.equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertThat(made.columns()).extracting(BankFileColumn::heading).containsExactly("Sr", "Account", "Amount");
        assertThat(made.fileFormat()).isEqualTo(ExportFormat.CSV);
    }

    @Test
    @DisplayName("a name taken between proposal and Confirm is refused with the service's words; still one")
    void takenMeanwhile() throws Exception {
        assertThat(registry.execute(BankFileTemplateToolProvider.TOOL, ctx, json.readTree(String.format(INPUT, name)))
                        .ok())
                .isTrue();
        templates.create(new BankFileTemplateRequest(
                name,
                ExportFormat.CSV,
                true,
                List.of(new BankFileColumn("X", BankFileColumnSource.SERIAL, null, null, null))));

        assertThatThrownBy(() -> exec.execute(ctx, proposed.get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
        assertThat(count(name)).isEqualTo(1);

        // Asked again, the tool says so before any card.
        AiToolResult again =
                registry.execute(BankFileTemplateToolProvider.TOOL, ctx, json.readTree(String.format(INPUT, name)));
        assertThat(again.error()).contains("already exists");
    }

    @Test
    @DisplayName("a caller without payroll.settings.manage is never offered the tool, and a call to it makes nothing")
    void notOffered() throws Exception {
        when(permissions.holds("payroll.settings.manage")).thenReturn(false);

        assertThat(registry.toolsFor(ctx))
                .extracting(AiToolDescriptor::name)
                .doesNotContain(BankFileTemplateToolProvider.TOOL);
        AiToolResult result =
                registry.execute(BankFileTemplateToolProvider.TOOL, ctx, json.readTree(String.format(INPUT, name)));
        assertThat(result.ok()).isFalse();
        assertThat(proposed).isEmpty();
        assertThat(count(name)).isZero();
    }
}
