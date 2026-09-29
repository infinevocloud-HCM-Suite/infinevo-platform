package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The caller's employee, from {@code W-13.4}'s login link: {@link EmployeeService#currentEmployee()}
 * resolves the token's subject to its {@code core.user_account} row and that row to the live employee
 * linked to it in the bound tenant. A login linked to no employee is nobody, so {@code read_own}
 * admits nothing for it.
 */
@Component
class EmployeeDocumentOwnerResolver implements DocumentOwnerResolver {

    private final EmployeeService employees;

    EmployeeDocumentOwnerResolver(EmployeeService employees) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
    }

    @Override
    public Optional<UUID> currentEmployeeId() {
        return employees.currentEmployee().map(EmployeeResponse::id);
    }
}
