package com.infinevo.core.employee;

import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paginated, filtered, free-text search over a tenant's employees (W-13.3, spec section 4).
 *
 * <p>The controller delegates here rather than calling the repository directly, because three
 * concerns belong in the service and not in the controller.
 *
 * <ol>
 *   <li><strong>Page size clamping.</strong> A client that asks for {@code size=10000} gets 100
 *       rows, not a rejected request and not a database full-scan. The default is 25.
 *   <li><strong>Soft-delete guard.</strong> The default search excludes deleted rows. Setting
 *       {@code includeDeleted=true} requires {@code core.employee.delete}; without it the caller
 *       gets {@code 403 FORBIDDEN}, not a silently filtered page.
 *   <li><strong>Tenant scoping.</strong> The tenant comes from {@link TenantContext}, never from a
 *       parameter. Combined with the repository's {@code tenantId = :tenantId} and the database's
 *       row-level security, three independent layers enforce the same boundary.
 * </ol>
 */
@Service
public class EmployeeQueryService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

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

        Page<Employee> page = repository.search(tenantId, q, status, includeDeleted, clamped);
        return page.map(EmployeeSummaryResponse::from);
    }

    /**
     * Clamps the page size to [{@code 1}, {@link #MAX_PAGE_SIZE}] and applies the default sort
     * ({@code lastName,asc}) when the client sends none.
     */
    private static Pageable clamp(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by("lastName").ascending();
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
