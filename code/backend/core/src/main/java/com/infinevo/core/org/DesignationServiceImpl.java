package com.infinevo.core.org;

import com.infinevo.core.employee.EmployeeRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The designation service (W-14.1). See {@link DepartmentServiceImpl} — the two are the same service over two tables. */
@Service
public class DesignationServiceImpl
        extends AbstractOrgMasterServiceImpl<Designation, DesignationRequest, DesignationResponse>
        implements DesignationService {

    /** The unique index from {@code V012__designation.sql}. See {@link DepartmentServiceImpl}. */
    private static final String CODE_INDEX = "idx_designation_tenant_code";

    private final EmployeeRepository employeeRepository;

    public DesignationServiceImpl(DesignationRepository designationRepository, EmployeeRepository employeeRepository) {
        super(designationRepository);
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    protected String kind() {
        return "designation";
    }

    @Override
    protected String uniqueCodeIndexName() {
        return CODE_INDEX;
    }

    @Override
    protected Designation newEntity(UUID tenantId, String actor) {
        return new Designation(tenantId, actor);
    }

    @Override
    protected DesignationResponse toResponse(Designation entity) {
        return DesignationResponse.from(entity);
    }

    @Override
    protected long countAssigned(UUID tenantId, UUID id) {
        return employeeRepository.countByTenantIdAndDesignation_Id(tenantId, id);
    }
}
