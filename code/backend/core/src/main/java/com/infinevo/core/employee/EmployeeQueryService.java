package com.infinevo.core.employee;

import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paginated, filtered, free-text search over a tenant's employees (W-13.3, spec section 4).
 */
@Service
public class EmployeeQueryService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "firstName", "lastName", "employeeNumber", "dateOfJoining", "status", "workEmail", "createdAt");

    private final EmployeeRepository repository;
    private final PermissionService permissionService;

    public EmployeeQueryService(EmployeeRepository repository, PermissionService permissionService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /**
     * Lists and searches employees in the bound tenant.
     *
     * @param q            free-text prefix, matched against first name, last name, employee
     *                     number and work email; {@code null} or blank means no text filter
     * @param status       employment status filter; {@code null} means all statuses
     * @param includeDeleted if true, includes soft-deleted rows — requires
     *                       {@code core.employee.delete}
     * @param pageable     page, size and sort from the request; size is clamped
     * @return a page of summaries, never another tenant's rows
     */
    @Transactional(readOnly = true)
    public Page<EmployeeSummaryResponse> search(
            String q, EmploymentStatus status, boolean includeDeleted, Pageable pageable) {

        UUID tenantId = TenantContext.require();

        if (includeDeleted) {
            permissionService.require("core.employee.delete");
        }

        Pageable clamped = clamp(pageable);
        String safeQ = (q != null && !q.trim().isEmpty())
                ? q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                : null;

        Page<Employee> page = repository.search(tenantId, safeQ, status, includeDeleted, clamped);
        return page.map(EmployeeSummaryResponse::from);
    }

    /**
     * Clamps the page size to [{@code 1}, {@link #MAX_PAGE_SIZE}], validates sort fields, and applies
     * deterministic tie-breaking ({@code id ASC}).
     */
    private static Pageable clamp(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort();

        if (sort.isSorted()) {
            for (Sort.Order order : sort) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
                }
            }
            if (sort.getOrderFor("id") == null) {
                sort = sort.and(Sort.by("id").ascending());
            }
        } else {
            sort = Sort.by("lastName")
                    .ascending()
                    .and(Sort.by("firstName").ascending())
                    .and(Sort.by("id").ascending());
        }

        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
