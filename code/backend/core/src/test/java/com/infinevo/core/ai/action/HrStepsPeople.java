package com.infinevo.core.ai.action;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.employee.EmploymentStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;

/**
 * W-114.29 unit tests: the tenant's people as {@link EmployeeQueryService} (what the tools search) and
 * {@link EmployeeRepository} (what the executors find again by number) see them. A removed person is gone from both.
 */
final class HrStepsPeople {

    final EmployeeQueryService query = mock(EmployeeQueryService.class);
    final EmployeeRepository repository = mock(EmployeeRepository.class);
    private final List<EmployeeSummaryResponse> live = new ArrayList<>();

    HrStepsPeople() {
        when(query.search(any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            String prefix = ((String) inv.getArgument(0)).toLowerCase();
            return new PageImpl<>(live.stream()
                    .filter(e -> e.employeeNumber().toLowerCase().startsWith(prefix)
                            || e.firstName().toLowerCase().startsWith(prefix))
                    .toList());
        });
        when(repository.findByTenantIdAndEmployeeNumberAndDeletedFalse(any(), anyString()))
                .thenReturn(Optional.empty());
    }

    /** Adds a live employee and answers its id. */
    UUID add(String number, String first, String last) {
        UUID id = UUID.randomUUID();
        EmployeeSummaryResponse summary = new EmployeeSummaryResponse(
                id,
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
                false);
        live.add(summary);
        Employee entity = mock(Employee.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getEmployeeNumber()).thenReturn(number);
        when(entity.getFirstName()).thenReturn(first);
        when(entity.getLastName()).thenReturn(last);
        when(repository.findByTenantIdAndEmployeeNumberAndDeletedFalse(any(), eq(number)))
                .thenReturn(Optional.of(entity));
        return id;
    }

    /** Removes the employee (soft-delete): neither the search nor the lookup by number finds it. */
    void remove(String number) {
        live.removeIf(e -> e.employeeNumber().equals(number));
        when(repository.findByTenantIdAndEmployeeNumberAndDeletedFalse(any(), eq(number)))
                .thenReturn(Optional.empty());
    }
}
