package com.infinevo.hrms.overtime;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.overtime.OvertimeEntry;
import com.infinevo.core.overtime.OvertimeResponse;
import com.infinevo.core.overtime.OvertimeService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link OvertimeRequestWorkflowService} (W-40.6 §4).
 *
 * <p>The row and the approval instance are written in one transaction, as
 * {@code payroll/.../reimbursement/ReimbursementClaimServiceImpl.java:125-140} does. The row is {@code core}'s and is
 * reached only through {@link OvertimeService}; this module holds no mapping of it.
 */
@Service
public class OvertimeRequestWorkflowServiceImpl implements OvertimeRequestWorkflowService {

    private final OvertimeService overtimeService;
    private final ApprovalService approvalService;
    private final EmployeeService employeeService;

    public OvertimeRequestWorkflowServiceImpl(
            OvertimeService overtimeService, ApprovalService approvalService, EmployeeService employeeService) {
        this.overtimeService = Objects.requireNonNull(overtimeService, "overtimeService must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional
    public OvertimeResponse submit(OvertimeRequestSubmission submission) {
        EmployeeResponse employee = requireCurrentEmployee();
        if (submission == null) {
            throw new OvertimeService.ValidationException(Map.of("body", "a request body is required"));
        }
        // The employee is the caller, never a field of the body (legacy OvertimeRequestController.java:35-41).
        OvertimeResponse saved = overtimeService.submit(new OvertimeEntry(
                employee.id(), submission.overtimeDate(), submission.hours(), null, submission.remarks()));
        approvalService.start(ApprovalFlowType.OVERTIME, new SubjectRef(SUBJECT_TABLE, saved.id()), employee.id());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OvertimeResponse> mine(LocalDate from, LocalDate to) {
        EmployeeResponse employee = requireCurrentEmployee();
        if (from == null || to == null) {
            throw new OvertimeService.ValidationException(Map.of("from", "from and to are required"));
        }
        return overtimeService.list(from, to, employee.id());
    }

    /** Refused as {@code ReimbursementClaimServiceImpl.java:64} refuses it (W-40.3 §4). */
    private EmployeeResponse requireCurrentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
    }
}
