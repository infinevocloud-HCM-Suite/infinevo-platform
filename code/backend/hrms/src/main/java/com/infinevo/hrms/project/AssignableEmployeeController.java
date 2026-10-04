package com.infinevo.hrms.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-48.1 section 4 — the employee picker behind project manager and team member fields. At most ten
 * {@code ACTIVE} employees of the bound tenant whose name or number matches {@code q}, through core's
 * employee search. A {@code q} under two characters gives an empty list without searching.
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequiresAction("hrms.project.manage")
@RequestMapping("/api/v1/hrms/employees/assignable")
public class AssignableEmployeeController {

    static final int MAX_RESULTS = 10;
    static final int MIN_QUERY_LENGTH = 2;

    private final EmployeeQueryService employeeQueryService;

    public AssignableEmployeeController(EmployeeQueryService employeeQueryService) {
        this.employeeQueryService =
                Objects.requireNonNull(employeeQueryService, "employeeQueryService must not be null");
    }

    /** One picker row: id, number and display name. */
    public record AssignableEmployee(
            @JsonProperty("employee_id") UUID employeeId,
            @JsonProperty("employee_number") String employeeNumber,
            @JsonProperty("name") String name) {

        static AssignableEmployee from(EmployeeSummaryResponse e) {
            String name = Stream.of(e.firstName(), e.lastName())
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(" "));
            return new AssignableEmployee(e.id(), e.employeeNumber(), name.isBlank() ? e.employeeNumber() : name);
        }
    }

    @GetMapping
    public ApiResponse<List<AssignableEmployee>> search(@RequestParam(name = "q", required = false) String q) {
        String trimmed = q != null ? q.trim() : "";
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return ApiResponse.success("Assignable employees retrieved successfully", List.of());
        }
        List<AssignableEmployee> rows = employeeQueryService
                .search(
                        trimmed,
                        EmploymentStatus.ACTIVE,
                        false,
                        PageRequest.of(
                                0,
                                MAX_RESULTS,
                                Sort.by("firstName")
                                        .ascending()
                                        .and(Sort.by("lastName").ascending())))
                .map(AssignableEmployee::from)
                .getContent();
        return ApiResponse.success("Assignable employees retrieved successfully", rows);
    }
}
