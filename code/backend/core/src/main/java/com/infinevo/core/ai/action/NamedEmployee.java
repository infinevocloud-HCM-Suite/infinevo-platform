package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * W-114.29: a person named in chat by employee number or full name, found within the bound tenant as
 * {@code payroll/ai/EmployeeTdsAiToolProvider.java} finds one. Soft-deleted employees are never found, so a hidden one
 * answers like an unknown one. Several matches list up to {@link #MAX_MATCHES} names with their numbers.
 */
final class NamedEmployee {

    static final String NO_EMPLOYEE = "No employee with that number or name.";
    static final String MANY = "More than one employee has that name. Ask which one, by employee number: ";
    static final String NO_NUMBER = "That employee has no employee number. Use the screen for this one.";
    static final String GONE = "That employee is no longer there.";
    static final String NO_SEARCH = "You cannot search employees, so this step cannot find a colleague here."
            + " Use the screen, or ask an administrator for employee access.";
    static final int MAX_MATCHES = 10;

    /** The search's page cap ({@code EmployeeQueryService}), and how many pages are read at most. */
    static final int SEARCH_PAGE = 100;

    static final int MAX_SEARCH_PAGES = 50;

    private NamedEmployee() {}

    /** The one live employee with that number, or else that full name; otherwise a {@link BadInput} to relay. */
    static EmployeeSummaryResponse one(EmployeeQueryService employees, String who) {
        // The same answer whoever is named, before any lookup: the screen's picker needs this action too.
        if (!employees.canSearch()) {
            throw new BadInput(NO_SEARCH);
        }
        String wanted = who.trim().toLowerCase(Locale.ROOT);
        String prefix = who.trim().split("\\s+")[0];
        // Every match of the first word, a page at a time in a fixed order: an exact match may be past the first page.
        List<EmployeeSummaryResponse> found = new ArrayList<>();
        for (int page = 0; page < MAX_SEARCH_PAGES; page++) {
            Page<EmployeeSummaryResponse> p = employees.search(
                    prefix, null, false, PageRequest.of(page, SEARCH_PAGE, Sort.by("employeeNumber", "id")));
            p.getContent().stream().filter(e -> !e.isDeleted()).forEach(found::add);
            if (!p.hasNext()) {
                break;
            }
        }
        List<EmployeeSummaryResponse> matches = found.stream()
                .filter(e -> e.employeeNumber() != null
                        && e.employeeNumber().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        if (matches.isEmpty()) {
            matches = found.stream()
                    .filter(e -> full(e.firstName(), e.middleName(), e.lastName())
                                    .toLowerCase(Locale.ROOT)
                                    .equals(wanted)
                            || full(e.firstName(), null, e.lastName())
                                    .toLowerCase(Locale.ROOT)
                                    .equals(wanted))
                    .toList();
        }
        if (matches.isEmpty()) {
            throw new BadInput(NO_EMPLOYEE);
        }
        if (matches.size() > 1) {
            throw new BadInput(MANY
                    + matches.stream()
                            .limit(MAX_MATCHES)
                            .map(NamedEmployee::label)
                            .collect(Collectors.joining(", "))
                    + ".");
        }
        EmployeeSummaryResponse e = matches.get(0);
        if (e.employeeNumber() == null || e.employeeNumber().isBlank()) {
            throw new BadInput(NO_NUMBER);
        }
        return e;
    }

    /** The live employee a stored payload names by number, found again within the tenant. */
    static Optional<Employee> byNumber(EmployeeRepository employees, UUID tenantId, String number) {
        if (number == null) {
            return Optional.empty();
        }
        return employees.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, number);
    }

    /** The live employee a stored payload names, or {@link IllegalStateException} for the card. */
    static Employee require(EmployeeRepository employees, UUID tenantId, String number) {
        return byNumber(employees, tenantId, number).orElseThrow(() -> new IllegalStateException(GONE));
    }

    /** What the card shows: "Asha Rao (E-001)". */
    static String label(EmployeeSummaryResponse e) {
        return full(e.firstName(), e.middleName(), e.lastName()) + " (" + e.employeeNumber() + ")";
    }

    static String label(Employee e) {
        return full(e.getFirstName(), e.getMiddleName(), e.getLastName()) + " (" + e.getEmployeeNumber() + ")";
    }

    /** For the card when the employee is gone: the number alone. */
    static String label(EmployeeRepository employees, UUID tenantId, String number) {
        return byNumber(employees, tenantId, number).map(NamedEmployee::label).orElse(number);
    }

    static String full(String first, String middle, String last) {
        return Stream.of(first, middle, last)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
