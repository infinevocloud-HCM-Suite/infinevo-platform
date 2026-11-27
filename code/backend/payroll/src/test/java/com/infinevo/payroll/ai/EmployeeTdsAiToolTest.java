package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.payroll.tds.TdsSpread;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.27 spec, Tests: {@code payroll.employee_tds} over mocked services. */
class EmployeeTdsAiToolTest {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final ObjectMapper json = new ObjectMapper();
    private final AiToolContext ctx = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);
    private final UUID ashaId = UUID.randomUUID();

    private EmployeeQueryService employees;
    private EmployeeTdsService tds;
    private AiTool tool;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeQueryService.class);
        tds = mock(EmployeeTdsService.class);
        TenantClock clock = mock(TenantClock.class);
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 27));
        tool = new EmployeeTdsAiToolProvider(employees, tds, clock, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
        EmployeeTds record = mock(EmployeeTds.class);
        when(record.getAnnualGross()).thenReturn(new BigDecimal("900000"));
        when(record.getAnnualTaxableIncome()).thenReturn(new BigDecimal("825000"));
        when(record.getAnnualTax()).thenReturn(new BigDecimal("52000"));
        when(record.getEffectiveFromPeriod()).thenReturn("2026-04");
        when(record.getNote()).thenReturn("Recalculated after Asha's medical bills");
        when(tds.active(ashaId, "2026-2027")).thenReturn(Optional.of(record));
        when(tds.yearToDate(ashaId, "2026-2027")).thenReturn(new BigDecimal("30000"));
        when(tds.spread(any(), any()))
                .thenReturn(new TdsSpread(
                        new BigDecimal("30000"),
                        new BigDecimal("22000"),
                        4,
                        Money.of(new BigDecimal("5500")),
                        YearMonth.of(2026, 12)));
    }

    private static EmployeeSummaryResponse person(UUID id, String number, String first, String last) {
        return new EmployeeSummaryResponse(
                id,
                number,
                first,
                null,
                last,
                null,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false);
    }

    private void found(EmployeeSummaryResponse... people) {
        when(employees.search(anyString(), isNull(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(people)));
    }

    private AiToolResult call(String input) throws Exception {
        return tool.execute(ctx, json.readTree(input));
    }

    @Test
    @DisplayName("needs payroll.tax_declaration.read, the TDS card's action, and no employee record")
    void declaration() {
        assertThat(tool.name()).isEqualTo("payroll.employee_tds");
        assertThat(tool.requiredAction()).isEqualTo("payroll.tax_declaration.read");
        assertThat(tool.needsEmployee()).isFalse();
    }

    @Test
    @DisplayName("by number: the card's figures as two-decimal text; no id and no note text")
    void byNumber() throws Exception {
        found(person(ashaId, "E-014", "Asha", "Rao"));

        JsonNode data = call("{\"employee\":\"e-014\"}").data();

        assertThat(data.path("name").asText()).isEqualTo("Asha Rao");
        assertThat(data.path("financialYear").asText()).isEqualTo("2026-2027");
        assertThat(data.path("annualTax").asText()).isEqualTo("52000.00");
        assertThat(data.path("deductedSoFar").asText()).isEqualTo("30000.00");
        assertThat(data.path("remaining").asText()).isEqualTo("22000.00");
        assertThat(data.path("monthlyShare").asText()).isEqualTo("5500.00");
        assertThat(data.path("monthsLeft").asInt()).isEqualTo(4);
        assertThat(UUID_TEXT.matcher(data.toString()).find()).isFalse();
        assertThat(data.toString()).doesNotContain("medical");
    }

    @Test
    @DisplayName("an exact match past the first page of the search is still found")
    void secondPage() throws Exception {
        List<EmployeeSummaryResponse> first = new java.util.ArrayList<>();
        for (int i = 0; i < EmployeeTdsAiToolProvider.SEARCH_PAGE; i++) {
            first.add(person(UUID.randomUUID(), "E-01" + i, "Asha", "Other" + i));
        }
        when(employees.search(anyString(), isNull(), anyBoolean(), any())).thenAnswer(inv -> {
            org.springframework.data.domain.Pageable p = inv.getArgument(3);
            return p.getPageNumber() == 0
                    ? new PageImpl<>(first, p, EmployeeTdsAiToolProvider.SEARCH_PAGE + 1L)
                    : new PageImpl<>(
                            List.of(person(ashaId, "E-014", "Asha", "Rao")),
                            p,
                            EmployeeTdsAiToolProvider.SEARCH_PAGE + 1L);
        });

        assertThat(call("{\"employee\":\"Asha Rao\"}").data().path("annualTax").asText())
                .isEqualTo("52000.00");
    }

    @Test
    @DisplayName("by full name; two with that name: both listed, no figures")
    void byName_andTwo() throws Exception {
        found(person(ashaId, "E-014", "Asha", "Rao"));
        assertThat(call("{\"employee\":\"asha rao\"}").data().path("annualTax").asText())
                .isEqualTo("52000.00");

        found(person(ashaId, "E-014", "Asha", "Rao"), person(UUID.randomUUID(), "E-099", "Asha", "Rao"));
        JsonNode two = call("{\"employee\":\"Asha Rao\"}").data();
        assertThat(two.path("message").asText()).isEqualTo(EmployeeTdsAiToolProvider.MANY);
        assertThat(two.path("matches")).hasSize(2);
        assertThat(two.has("annualTax")).isFalse();
    }

    @Test
    @DisplayName("unknown reads like hidden; no record for the year says so")
    void unknownAndNoRecord() throws Exception {
        found();
        assertThat(call("{\"employee\":\"E-404\"}").data().path("message").asText())
                .isEqualTo(EmployeeTdsAiToolProvider.NO_EMPLOYEE);
        verify(tds, never()).active(any(), any());

        found(person(ashaId, "E-014", "Asha", "Rao"));
        JsonNode none =
                call("{\"employee\":\"E-014\",\"financialYear\":\"2025-26\"}").data();
        assertThat(none.path("financialYear").asText()).isEqualTo("2025-2026");
        assertThat(none.path("message").asText()).isEqualTo(EmployeeTdsAiToolProvider.NO_RECORD);
    }

    @Test
    @DisplayName("deleted employees are never searched for: includeDeleted is false")
    void liveOnly() throws Exception {
        found(person(ashaId, "E-014", "Asha", "Rao"));
        call("{\"employee\":\"E-014\"}");
        verify(employees).search(eq("E-014"), isNull(), eq(false), any());
    }
}
