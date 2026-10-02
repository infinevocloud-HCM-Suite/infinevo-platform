package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

/**
 * W-30.2 §7 — who a named off-cycle run includes: bank required, salary recorded but never required,
 * a leaver inside the period in, anyone outside it a {@code 400} naming them.
 */
class OffCycleInclusionRulesTest {

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
    @DisplayName("A named leaver terminated inside the period is INCLUDED — final dues are an off-cycle run")
    void leaverInsideThePeriodIsIncluded() {
        EmployeeResponse leaver =
                employee(EmploymentStatus.TERMINATED, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 4, 10));
        UUID versionId = givenSalary(leaver.id());
        givenBank(leaver.id());

        List<OffCycleInclusion> rows = inclusion.forNamed(TENANT, List.of(leaver), START, END);

        assertThat(rows).containsExactly(OffCycleInclusion.included(leaver.id(), versionId));
    }

    @Test
    @DisplayName("No bank section: SKIPPED NO_BANK_DETAILS, salary never asked")
    void noBankIsSkipped() {
        EmployeeResponse employee = employee(EmploymentStatus.ACTIVE, LocalDate.of(2025, 1, 1), null);
        when(bankService.find(employee.id())).thenReturn(Optional.empty());

        List<OffCycleInclusion> rows = inclusion.forNamed(TENANT, List.of(employee), START, END);

        assertThat(rows).containsExactly(OffCycleInclusion.skipped(employee.id(), SkipReason.NO_BANK_DETAILS));
        verify(salaryService, never()).versionInForce(any(), any(), any());
    }

    @Test
    @DisplayName("No salary version: INCLUDED with salary_version_id null — a joining bonus before the CTC")
    void noSalaryIsStillIncluded() {
        EmployeeResponse joiner = employee(EmploymentStatus.ACTIVE, LocalDate.of(2026, 4, 20), null);
        givenBank(joiner.id());
        when(salaryService.versionInForce(TENANT, joiner.id(), END)).thenThrow(new SalaryNotFoundException("none"));

        List<OffCycleInclusion> rows = inclusion.forNamed(TENANT, List.of(joiner), START, END);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.inclusionStatus()).isEqualTo(InclusionStatus.INCLUDED);
            assertThat(row.skipReason()).isNull();
            assertThat(row.salaryVersionId()).isNull();
        });
    }

    @Test
    @DisplayName("Terminated before the period, or joined after it: 400 naming every such employee")
    void outsideThePeriodIsRefusedByName() {
        EmployeeResponse gone =
                employee(EmploymentStatus.TERMINATED, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 3, 31));
        EmployeeResponse future = employee(EmploymentStatus.ACTIVE, LocalDate.of(2026, 5, 1), null);
        EmployeeResponse fine = employee(EmploymentStatus.ACTIVE, LocalDate.of(2025, 1, 1), null);

        assertThatThrownBy(() -> inclusion.forNamed(TENANT, List.of(gone, fine, future), START, END))
                .isInstanceOf(EmployeeNotInRunException.class)
                .hasMessageContaining(gone.id().toString())
                .hasMessageContaining(future.id().toString())
                .message()
                .doesNotContain(fine.id().toString());
        verify(bankService, never()).find(any());
    }

    @Test
    @DisplayName("A suspended employee is not considered: 400")
    void suspendedIsRefused() {
        EmployeeResponse suspended = employee(EmploymentStatus.SUSPENDED, LocalDate.of(2025, 1, 1), null);

        assertThatThrownBy(() -> inclusion.forNamed(TENANT, List.of(suspended), START, END))
                .isInstanceOf(EmployeeNotInRunException.class);
    }

    @Test
    @DisplayName("An included row may carry no version; a skipped row still needs a reason and no version")
    void rowShape() {
        UUID id = UUID.randomUUID();
        assertThat(OffCycleInclusion.included(id, null).salaryVersionId()).isNull();
        assertThatThrownBy(() -> new OffCycleInclusion(id, InclusionStatus.SKIPPED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OffCycleInclusion(id, InclusionStatus.SKIPPED, SkipReason.NO_BANK_DETAILS, id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OffCycleInclusion(id, InclusionStatus.INCLUDED, SkipReason.NO_SALARY, null))
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
