package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalDefinitionRequest;
import com.infinevo.core.approval.ApprovalDefinitionService;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepDefinition;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.approval.CommentScope;
import com.infinevo.core.approval.OutcomeDispatcher;
import com.infinevo.core.approval.StepOrdering;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * W-16.3, spec section 7 &amp; 8 — {@code LeaveRequestApprovalIT}.
 *
 * <p>Verifies leave approval lifecycle through the engine and absence of direct approval API:
 * <ul>
 *   <li>Leave request raised with submit=true starts an approval instance.
 *   <li>When the approval instance is approved through the engine, {@link LeaveApprovalOutcomeHandler} transitions the request to {@code APPROVED}.
 *   <li>Asserts the strict absence of any direct {@code approve} method on {@link LeaveRequestService} and {@link LeaveRequestController}.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
class LeaveRequestApprovalIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private OutcomeDispatcher outcomeDispatcher;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID requesterId;
    private UUID managerId;
    private UUID leaveTypeId;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.seedTenants();
        LeaveTestSchema.clearAll();

        requesterId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-REQ-1", "Requester", "req@a.test");
        managerId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-MGR-1", "Manager", "mgr@a.test");

        TenantContext.set(TENANT_A);

        // Configure single step approval for LEAVE assigned to managerId
        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(
                                ApproverKind.NAMED_EMPLOYEE, managerId.toString(), 3, false))));

        LeaveTypeResponse tA = leaveTypeService.createLeaveType(
                TENANT_A,
                new LeaveTypeRequest(
                        "Paid Leave",
                        "PL",
                        true,
                        LeaveUnit.DAYS,
                        true,
                        LocalDate.now().minusYears(1),
                        null));
        leaveTypeId = tA.id();

        // Configure mock employee service
        Mockito.when(employeeService.currentEmployee())
                .thenReturn(Optional.of(new EmployeeResponse(
                        managerId,
                        TENANT_A,
                        "EMP-MGR-1",
                        "Manager",
                        null,
                        "M",
                        null,
                        LocalDate.of(2024, 1, 1),
                        null,
                        EmploymentStatus.ACTIVE,
                        "mgr@a.test",
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        java.time.Instant.now(),
                        java.time.Instant.now())));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("neither LeaveRequestService nor LeaveRequestController exposes a direct approve method")
    void noDirectApproveMethodExposed() {
        boolean serviceHasApprove = Arrays.stream(LeaveRequestService.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().contains("approve"));
        assertThat(serviceHasApprove)
                .withFailMessage(
                        "LeaveRequestService must NOT expose any approve method; approval is owned by the engine")
                .isFalse();

        boolean controllerHasApprove = Arrays.stream(LeaveRequestController.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().contains("approve"));
        assertThat(controllerHasApprove)
                .withFailMessage("LeaveRequestController must NOT expose any approve endpoint")
                .isFalse();
    }

    @Test
    @DisplayName("a request raised and approved through the engine ends APPROVED with decided_at stamped")
    void requestApprovedViaEngine() {
        TenantContext.set(TENANT_A);

        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveApplyRequest applyReq =
                new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Vacation trip", null, true);

        LeaveRequestResponse created = leaveRequestService.createRequest(TENANT_A, requesterId, applyReq);
        assertThat(created.status()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(created.approvalInstanceId()).isNotNull();

        UUID instanceId = created.approvalInstanceId();
        List<ApprovalStep> steps = stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId);
        assertThat(steps).hasSize(1);
        ApprovalStep step = steps.get(0);
        assertThat(step.getAssigneeEmployeeId()).isEqualTo(managerId);

        // Manager decides through the engine API
        approvalService.decide(step.getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved enjoy"));
        outcomeDispatcher.retryPendingOutcomes(TENANT_A);

        // Verify leave request is now APPROVED
        LeaveRequest updated = leaveRequestRepository
                .findByIdAndTenantId(created.id(), TENANT_A)
                .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(updated.getDecidedAt()).isNotNull();
    }
}
