package com.infinevo.hrms.timesheet;

import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of {@link TimesheetProjectEntryService} (W-42.3). */
@Service
@Transactional(readOnly = true)
public class TimesheetProjectEntryServiceImpl implements TimesheetProjectEntryService {

    static final String ACTION_APPROVE = "hrms.timesheet.approve";

    private final TimesheetProjectEntryRepository entryRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalStepRepository stepRepository;
    private final EmployeeService employeeService;
    private final TimesheetNames names;

    public TimesheetProjectEntryServiceImpl(
            TimesheetProjectEntryRepository entryRepository,
            ApprovalInstanceRepository instanceRepository,
            ApprovalStepRepository stepRepository,
            EmployeeService employeeService,
            TimesheetNames names) {
        this.names = Objects.requireNonNull(names, "names must not be null");
        this.entryRepository = Objects.requireNonNull(entryRepository, "entryRepository must not be null");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    public TimesheetProjectEntryResponse get(UUID entryId) {
        UUID tenantId = TenantContext.require();
        UUID me = employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .orElseThrow(() -> new PermissionDeniedException(ACTION_APPROVE));

        TimesheetProjectEntry entry =
                entryRepository.findByTenantIdAndId(tenantId, entryId).orElseThrow(() -> notFound(entryId));

        // Any instance for this line counts: an earlier instance's approver may need to see what was decided.
        boolean assigned = instanceRepository
                .findByTenantIdAndSubjectTableAndSubjectId(tenantId, TimesheetSubmitService.SUBJECT_TABLE, entryId)
                .stream()
                .map(ApprovalInstance::getId)
                .anyMatch(instanceId ->
                        stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId).stream()
                                .anyMatch(step -> me.equals(step.getAssigneeEmployeeId())));
        if (!assigned) {
            throw notFound(entryId);
        }
        TimesheetNames.Names n = names.forEntry(tenantId, entry);
        return TimesheetProjectEntryResponse.from(entry, n.employees(), n.projects(), n.tasks());
    }

    private static ResourceNotFoundException notFound(UUID entryId) {
        return new ResourceNotFoundException("No timesheet project entry " + entryId);
    }
}
