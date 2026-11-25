package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-114.7, the two tax tools against real Postgres through the real {@link AiToolRegistry}: a year with no declaration
 * answers the plain sentence and starts no draft; once the employee has opened the year on their screen, the
 * declaration and the summary come back - the summary's upsert of its row running in the tool's ordinary transaction,
 * which a read-only one would refuse.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayrollAiTaxToolsIT extends AbstractIntegrationTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    private PayslipService payslipService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private TaxSummaryService taxSummaryService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiToolRegistry registry;
    private UUID employeeId;
    private UUID sub;
    private String currentFy;

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @AfterAll
    static void tearDownAll() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeId = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-AI-1", "ai.tax@acme.com", "Asha", "Rao");
        LocalDate today = LocalDate.now();
        currentFy = FinancialYear.containing(today).label();

        TenantClock tenantClock = mock(TenantClock.class);
        when(tenantClock.today()).thenReturn(today);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        PayrollAiToolProvider provider = new PayrollAiToolProvider(
                payslipService,
                taxDeclarationService,
                taxSummaryService,
                employeeService,
                tenantClock,
                transactionManager);
        registry = new AiToolRegistry(permissions, entitlements, List.of(provider));

        sub = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sub.toString(), null, List.of()));
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));
    }

    @AfterEach
    void tearDown() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("no declaration this year: the plain sentence from both tools, and no draft is started")
    void noDeclaration_plainSentence_noDraft() {
        AiToolContext ctx = new AiToolContext(TaxDeclarationTestSchema.TENANT_A, sub, employeeId);

        AiToolResult declaration = registry.execute("payroll.my_tax_declaration", ctx, objectMapper.createObjectNode());
        AiToolResult summary = registry.execute("payroll.my_tax_summary", ctx, objectMapper.createObjectNode());

        assertThat(declaration.ok()).as(String.valueOf(declaration.error())).isTrue();
        assertThat(declaration.data().path("message").asText()).isEqualTo(PayrollAiToolProvider.NO_DECLARATION);
        assertThat(summary.data().path("message").asText()).isEqualTo(PayrollAiToolProvider.NO_DECLARATION);
        assertThat(taxDeclarationService.find(employeeId, currentFy)).isEmpty();
    }

    @Test
    @DisplayName("after the screen opened the year: the declaration and the summary, amounts as text, no id")
    void declarationAndSummary_readBack() {
        taxDeclarationService.readOwn(currentFy);
        AiToolContext ctx = new AiToolContext(TaxDeclarationTestSchema.TENANT_A, sub, employeeId);

        AiToolResult declaration = registry.execute("payroll.my_tax_declaration", ctx, objectMapper.createObjectNode());
        assertThat(declaration.ok()).as(String.valueOf(declaration.error())).isTrue();
        assertThat(declaration.data().path("financialYear").asText()).isEqualTo(currentFy);
        assertThat(declaration.data().path("status").asText()).isEqualTo("DRAFT");
        assertNoUuid(declaration.data());

        AiToolResult summary = registry.execute(
                "payroll.my_tax_summary", ctx, objectMapper.createObjectNode().put("financialYear", currentFy));
        assertThat(summary.ok()).as(String.valueOf(summary.error())).isTrue();
        JsonNode declared = summary.data().path("declared");
        assertThat(declared.path("otherIncomeTotal").isTextual()).isTrue();
        assertThat(declared.path("otherIncomeTotal").asText()).isEqualTo("0.00");
        assertThat(summary.data().path("currency").asText()).isEqualTo("INR");
        assertNoUuid(summary.data());
    }

    private static void assertNoUuid(JsonNode data) {
        assertThat(ANY_UUID.matcher(data.toString()).find()).as(data.toString()).isFalse();
    }
}
