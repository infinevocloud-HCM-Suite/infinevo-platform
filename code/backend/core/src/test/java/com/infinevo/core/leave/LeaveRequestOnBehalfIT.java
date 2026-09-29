package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authorization and administrative creation integration tests for leave on-behalf (W-16.3, spec section 4 &amp; 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>{@code /on-behalf} with {@code core.leave.manage} creates an {@code APPROVED} request with no approval instance and {@code on_behalf = true}.
 *   <li>The same call with only {@code core.leave.apply} gets {@code 403 Forbidden}.
 * </ul>
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class LeaveRequestOnBehalfIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private UUID tenantId;
    private UUID employeeUserSub;
    private UUID adminUserSub;
    private UUID targetEmpId;
    private UUID leaveTypeId;

    @BeforeEach
    void setUp() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("LeaveOnBehalfIT " + UUID.randomUUID());
        TenantContext.set(tenantId);

        // 1. Employee with only employee role (holds core.leave.apply, but NOT core.leave.manage)
        employeeUserSub = UUID.randomUUID();
        UUID empAccountId = AuthzTestSchema.insertMember(tenantId, employeeUserSub, "regular@leave.test");
        AuthzTestSchema.grant(tenantId, empAccountId, AuthzTestSchema.roleId(tenantId, "employee"));
        UUID regularEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-REG-1", "Regular");
        employeeService.linkLogin(regularEmpId, empAccountId);

        // 2. Admin with tenant-admin role (holds core.leave.manage)
        adminUserSub = UUID.randomUUID();
        UUID adminAccountId = AuthzTestSchema.insertMember(tenantId, adminUserSub, "admin@leave.test");
        AuthzTestSchema.grant(tenantId, adminAccountId, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
        UUID adminEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-ADM-1", "Admin");
        employeeService.linkLogin(adminEmpId, adminAccountId);

        // 3. Target employee for whom on-behalf leave is recorded
        targetEmpId = AuthzTestSchema.insertEmployee(tenantId, "EMP-TGT-1", "Target");

        // 4. Create active leave type
        LeaveTypeResponse type = leaveTypeService.createLeaveType(
                tenantId,
                new LeaveTypeRequest(
                        "Casual Leave",
                        "CL",
                        true,
                        LeaveUnit.DAYS,
                        true,
                        LocalDate.now().minusYears(1),
                        null));
        leaveTypeId = type.id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Caller with only core.leave.apply gets 403 on /api/v1/leave-requests/on-behalf")
    void employeeWithoutManageActionGets403() throws Exception {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveOnBehalfRequest req =
                new LeaveOnBehalfRequest(targetEmpId, leaveTypeId, from, to, false, null, "Doctor note provided", null);

        mvc.perform(post("/api/v1/leave-requests/on-behalf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwt().jwt(b ->
                                b.subject(employeeUserSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Caller with core.leave.manage creates APPROVED request with on_behalf=true and no approval instance")
    void adminWithManageActionCreatesApprovedRequest() throws Exception {
        LocalDate from = LocalDate.now().plusDays(5);
        LocalDate to = LocalDate.now().plusDays(7);

        LeaveOnBehalfRequest req = new LeaveOnBehalfRequest(
                targetEmpId, leaveTypeId, from, to, false, null, "Admin recorded absence", null);

        String jsonResp = mvc.perform(post("/api/v1/leave-requests/on-behalf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwt().jwt(b ->
                                b.subject(adminUserSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.onBehalf").value(true))
                .andExpect(jsonPath("$.approvalInstanceId").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LeaveRequestResponse resp = objectMapper.readValue(jsonResp, LeaveRequestResponse.class);
        assertThat(resp.status()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(resp.onBehalf()).isTrue();
        assertThat(resp.approvalInstanceId()).isNull();

        // Verify in DB
        LeaveRequest dbRecord =
                leaveRequestRepository.findByIdAndTenantId(resp.id(), tenantId).orElseThrow();
        assertThat(dbRecord.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(dbRecord.isOnBehalf()).isTrue();
        assertThat(dbRecord.getApprovalInstanceId()).isNull();
        assertThat(dbRecord.getDecidedAt()).isNotNull();
    }
}
