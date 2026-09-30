package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeBankResponse;
import com.infinevo.core.employee.detail.EmployeeBankService;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.1 §7 — each row of the §3 inclusion table, with the salary and bank seams mocked. */
class PayRunInclusionRulesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END = LocalDate.of(2026, 4, 30);

    private EmployeeSalaryService salaryService;
    private EmployeeBankService bankService;
    private PayRunInclusionServiceImpl inclusion;

    @BeforeEach
    void setUp() {
        salaryService = mock(EmployeeSalaryService.class);
        bankService = mock(EmployeeBankService.class);
        inclusion = new PayRunInclusionServiceImpl(salaryService, bankService);
    }

    @Test
    @DisplayName("Salary in force and a bank section: INCLUDED with the salary version recorded")
    void includedRecordsSalaryVersion() {
        EmployeeResponse employee = employee(EmploymentStatus.ACTIVE, LocalDate.of(2025, 1, 1), null);
        UUID versionId = givenSalary(employee.id());
        givenBank(employee.id());

        List<InclusionDecision> decisions = inclusion.decide(TENANT, List.of(employee), START, END);

        assertThat(decisions).containsExactly(InclusionDecision.included(employee.id(), versionId));
        verify(salaryService).versionInForce(TENANT, employee.id(), END);
    }

    @Test
    @DisplayName("No salary version in force at period_end: SKIPPED NO_SALARY, bank never asked")
    void noSalaryIsSkipped() {
        EmployeeResponse employee = employee(EmploymentStatus.ACTIVE, LocalDate.of(2025, 1, 1), null);
        when(salaryService.versionInForce(eq(TENANT), eq(employee.id()), eq(END)))
                .thenThrow(new SalaryNotFoundException("none"));

        List<InclusionDecision> decisions = inclusion.decide(TENANT, List.of(employee), START, END);

        assertThat(decisions).containsExactly(InclusionDecision.skipped(employee.id(), SkipReason.NO_SALARY));
        verify(bankService, never()).find(any());
    }

    @Test
    @DisplayName("Salary but no bank section: SKIPPED NO_BANK_DETAILS")
    void noBankIsSkipped() {
        EmployeeResponse employee = employee(EmploymentStatus.ACTIVE, LocalDate.of(2025, 1, 1), null);
        givenSalary(employee.id());
        when(bankService.find(employee.id())).thenReturn(Optional.empty());

        List<InclusionDecision> decisions = inclusion.decide(TENANT, List.of(employee), START, END);

        assertThat(decisions).containsExactly(InclusionDecision.skipped(employee.id(), SkipReason.NO_BANK_DETAILS));
    }

    @Test
    @DisplayName("A leaver on period_start is considered; one who left the day before is not a row at all")
    void leaverBoundary() {
        EmployeeResponse onStart = employee(EmploymentStatus.TERMINATED, LocalDate.of(2025, 1, 1), START);
        EmployeeResponse dayBefore =
                employee(EmploymentStatus.TERMINATED, LocalDate.of(2025, 1, 1), START.minusDays(1));
        givenSalary(onStart.id());
        givenBank(onStart.id());

        List<InclusionDecision> decisions = inclusion.decide(TENANT, List.of(onStart, dayBefore), START, END);

        assertThat(decisions).extracting(InclusionDecision::employeeId).containsExactly(onStart.id());
        assertThat(PayRunInclusionServiceImpl.isConsidered(onStart, START, END)).isTrue();
        assertThat(PayRunInclusionServiceImpl.isConsidered(dayBefore, START, END))
                .isFalse();
    }

    @Test
    @DisplayName("Joined on period_end is considered; joined the day after is not")
    void joinerBoundary() {
        assertThat(PayRunInclusionServiceImpl.isConsidered(employee(EmploymentStatus.ACTIVE, END, null), START, END))
                .isTrue();
        assertThat(PayRunInclusionServiceImpl.isConsidered(
                        employee(EmploymentStatus.ACTIVE, END.plusDays(1), null), START, END))
                .isFalse();
    }

    @Test
    @DisplayName("SUSPENDED, and TERMINATED without a date, are not rows at all")
    void suspendedAndUndatedLeaverAreOut() {
        assertThat(PayRunInclusionServiceImpl.isConsidered(
                        employee(EmploymentStatus.SUSPENDED, LocalDate.of(2025, 1, 1), null), START, END))
                .isFalse();
        assertThat(PayRunInclusionServiceImpl.isConsidered(
                        employee(EmploymentStatus.TERMINATED, LocalDate.of(2025, 1, 1), null), START, END))
                .isFalse();
    }

    @Test
    @DisplayName("A decision cannot break the table's CHECK: skipped needs a reason, included a salary version")
    void decisionInvariant() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new InclusionDecision(id, InclusionStatus.SKIPPED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InclusionDecision(id, InclusionStatus.INCLUDED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InclusionDecision(id, InclusionStatus.INCLUDED, SkipReason.NO_SALARY, id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID givenSalary(UUID employeeId) {
        UUID versionId = UUID.randomUUID();
        SalaryVersionResponse version = mock(SalaryVersionResponse.class);
        when(version.id()).thenReturn(versionId);
        when(salaryService.versionInForce(TENANT, employeeId, END)).thenReturn(version);
        return versionId;
    }

    private void givenBank(UUID employeeId) {
        when(bankService.find(employeeId)).thenReturn(Optional.of(mock(EmployeeBankResponse.class)));
    }

    private static EmployeeResponse employee(EmploymentStatus status, LocalDate joined, LocalDate terminated) {
        return new EmployeeResponse(
                UUID.randomUUID(),
                TENANT,
                "E-" + UUID.randomUUID(),
                "First",
                null,
                "Last",
                "MALE",
                joined,
                terminated,
                status,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
