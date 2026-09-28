package com.infinevo.core.attendance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * W-39.1, spec section 7 — {@code AttendancePermissionIT}.
 *
 * <p>Verifies over HTTP with Spring Security and Redis permission checks:
 * <ul>
 *   <li>{@code 403} without {@code core.attendance.manage} on {@code PUT}.
 *   <li>{@code payroll-officer} (holds {@code core.attendance.read}) can {@code GET} but not {@code PUT}.
 *   <li>{@code hr} (holds {@code core.attendance.manage}) can {@code PUT} and {@code DELETE}.
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
class AttendancePermissionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID hrSub;
    private UUID payrollOfficerSub;
    private UUID employeeSub;
    private UUID employeeId;

    @BeforeEach
    void seed() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("AttendancePerm " + UUID.randomUUID());
        TenantContext.set(tenantId);

        employeeId = AuthzTestSchema.insertEmployee(tenantId, "ATT-PERM-1", "EmpOne");

        // HR member: holds hr role (which carries core.attendance.manage and core.attendance.read)
        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenantId, hrSub, "hr@attperm.test");
        AuthzTestSchema.grant(tenantId, hrAccount, AuthzTestSchema.roleId(tenantId, "hr"));

        // Payroll officer member: holds payroll-officer role (carries core.attendance.read, NOT core.attendance.manage)
        payrollOfficerSub = UUID.randomUUID();
        UUID poAccount = AuthzTestSchema.insertMember(tenantId, payrollOfficerSub, "po@attperm.test");
        AuthzTestSchema.grant(tenantId, poAccount, AuthzTestSchema.roleId(tenantId, "payroll-officer"));

        // Standard employee member: holds employee role (carries core.attendance.read_own only)
        employeeSub = UUID.randomUUID();
        UUID empAccount = AuthzTestSchema.insertMember(tenantId, employeeSub, "emp@attperm.test");
        AuthzTestSchema.grant(tenantId, empAccount, AuthzTestSchema.roleId(tenantId, "employee"));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Payroll officer can GET attendance but cannot PUT")
    void payrollOfficerCanGetButNotPut() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);

        // GET is allowed for payroll-officer (has core.attendance.read)
        mvc.perform(get("/api/v1/attendance")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .with(jwt().jwt(b ->
                                b.subject(payrollOfficerSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());

        // PUT is refused for payroll-officer (lacks core.attendance.manage)
        List<AttendanceEntry> body =
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.PRESENT, "Marked by payroll officer"));

        mvc.perform(put("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b ->
                                b.subject(payrollOfficerSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT without core.attendance.manage gets 403")
    void putWithoutAttendanceManageGets403() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);
        List<AttendanceEntry> body =
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.PRESENT, "Attempted write"));

        mvc.perform(put("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b ->
                                b.subject(employeeSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HR holding core.attendance.manage can PUT and DELETE attendance")
    void hrCanPutAndDelete() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);
        List<AttendanceEntry> body =
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.PRESENT, "Marked by HR"));

        MvcResult result = mvc.perform(put("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PRESENT"))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String idStr = objectMapper.readTree(responseJson).get(0).get("id").asText();
        UUID attendanceId = UUID.fromString(idStr);

        // Delete without manage permission gets 403
        mvc.perform(delete("/api/v1/attendance/" + attendanceId)
                        .with(jwt().jwt(b ->
                                b.subject(payrollOfficerSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());

        // Delete with manage permission gets 204
        mvc.perform(delete("/api/v1/attendance/" + attendanceId)
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }
}
