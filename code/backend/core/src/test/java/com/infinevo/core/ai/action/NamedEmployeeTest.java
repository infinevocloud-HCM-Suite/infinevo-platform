package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.employee.EmploymentStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** W-114.29: the person lookup every HR step shares. */
class NamedEmployeeTest {

    private static EmployeeSummaryResponse person(String number, String first, String last, boolean deleted) {
        return new EmployeeSummaryResponse(
                UUID.randomUUID(),
                number,
                first,
                null,
                last,
                null,
                null,
                EmploymentStatus.ACTIVE,
                LocalDate.parse("2026-01-01"),
                null,
                null,
                null,
                null,
                false,
                null,
                deleted);
    }

    @Test
    @DisplayName("an exact match past the search's first page of 100 is still found")
    void pastTheFirstPage() {
        List<EmployeeSummaryResponse> first = new ArrayList<>();
        for (int i = 0; i < NamedEmployee.SEARCH_PAGE; i++) {
            first.add(person("E-" + i, "Asha", "Other" + i, false));
        }
        EmployeeSummaryResponse wanted = person("E-900", "Asha", "Rao", false);
        EmployeeQueryService query = mock(EmployeeQueryService.class);
        when(query.search(eq("asha"), any(), anyBoolean(), any())).thenAnswer(inv -> {
            Pageable p = inv.getArgument(3);
            return p.getPageNumber() == 0
                    ? new PageImpl<>(first, p, NamedEmployee.SEARCH_PAGE + 1L)
                    : new PageImpl<>(List.of(wanted), p, NamedEmployee.SEARCH_PAGE + 1L);
        });

        assertThat(NamedEmployee.one(query, "asha rao")).isEqualTo(wanted);
    }

    @Test
    @DisplayName("a soft-deleted row answers like an unknown one; several list at most 10 names with numbers")
    void deletedAndMany() {
        EmployeeQueryService query = mock(EmployeeQueryService.class);
        List<EmployeeSummaryResponse> rows = new ArrayList<>();
        rows.add(person("E-1", "Gone", "Person", true));
        for (int i = 0; i < 12; i++) {
            rows.add(person("E-R" + i, "Ravi", "Kumar", false));
        }
        when(query.search(any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(rows));

        assertThatThrownBy(() -> NamedEmployee.one(query, "E-1"))
                .isInstanceOf(BadInput.class)
                .hasMessage(NamedEmployee.NO_EMPLOYEE);
        assertThatThrownBy(() -> NamedEmployee.one(query, "Ravi Kumar"))
                .isInstanceOf(BadInput.class)
                .hasMessageContaining("E-R0")
                .hasMessageContaining("E-R9")
                .hasMessageNotContaining("E-R10");
    }
}
