package com.infinevo.core.org;

import com.infinevo.core.employee.EmployeeRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The department service (W-14.1). Everything it does is {@link AbstractOrgMasterServiceImpl}'s; what
 * is here is what is genuinely department-specific, which is the entity, the response, the index name
 * and the count that makes a delete refusable.
 */
@Service
public class DepartmentServiceImpl
        extends AbstractOrgMasterServiceImpl<Department, DepartmentRequest, DepartmentResponse>
        implements DepartmentService {

    /**
     * The unique index from {@code V011__department.sql}. Named here so a constraint violation can be
     * told apart from every other integrity failure. If the index is ever renamed, rename it here too,
     * or duplicates start surfacing as 500s.
     */
    private static final String CODE_INDEX = "idx_department_tenant_code";

    private final EmployeeRepository employeeRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        super(departmentRepository);
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    protected String kind() {
        return "department";
    }

    @Override
    protected String uniqueCodeIndexName() {
        return CODE_INDEX;
    }

    @Override
    protected Department newEntity(UUID tenantId, String actor) {
        return new Department(tenantId, actor);
    }

    @Override
    protected DepartmentResponse toResponse(Department entity) {
        return DepartmentResponse.from(entity);
    }

    @Override
    protected long countAssigned(UUID tenantId, UUID id) {
        return employeeRepository.countByTenantIdAndDepartment_Id(tenantId, id);
    }
}
