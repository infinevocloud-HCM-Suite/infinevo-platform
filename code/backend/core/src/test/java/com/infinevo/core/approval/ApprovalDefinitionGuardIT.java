package com.infinevo.core.approval;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W-15.1, spec section 7 — {@code ApprovalDefinitionGuardIT}.
 *
 * <ul>
 *   <li>{@code GET /api/v1/approval-definitions} and {@code PUT /api/v1/approval-definitions/{flowType}}
 *       are 403 without {@code core.approval_definition.manage}.
 *   <li>Both are 200 with {@code core.approval_definition.manage}.
 * </ul>
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@Import(ApprovalDefinitionGuardIT.ProjectManagerResolver.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ApprovalDefinitionGuardIT extends AbstractIntegrationTest {

    /** A PROJECT_MANAGER resolver is registered, as hrms's will be (W-42.3), so only the step's shape is in question. */
    @TestConfiguration
    static class ProjectManagerResolver {

        @Bean
        ApproverResolver projectManagerResolver() {
            return new ApproverResolver() {
                @Override
                public ApproverKind kind() {
                    return ApproverKind.PROJECT_MANAGER;
                }

                @Override
                public Optional<UUID> resolve(UUID tenantId, UUID employeeId, String contextRef) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID hrSub;
    private UUID adminSub;

    @BeforeEach
    void seed() throws Exception {
        tenantId = AuthzTestSchema.insertTenant("ApprovalGuard " + UUID.randomUUID());
        TenantContext.set(tenantId);

        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenantId, hrSub, "hr@approvalguard.test");
        AuthzTestSchema.grant(tenantId, hrAccount, AuthzTestSchema.roleId(tenantId, "hr"));

        adminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenantId, adminSub, "admin@approvalguard.test");
        AuthzTestSchema.grant(tenantId, adminAccount, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
    }

    @AfterEach
    void unbind() throws Exception {
        TenantContext.clear();
        // These tests share a database with the employee tests, which delete employees; approval rows
        // left behind would block that delete through approval_instance's foreign key.
        ApprovalTestSchema.clearDefinitions();
    }

    @Test
    @DisplayName("GET without core.approval_definition.manage gets 403")
    void getWithoutManagePermissionGets403() throws Exception {
        mvc.perform(get("/api/v1/approval-definitions")
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT without core.approval_definition.manage gets 403")
    void putWithoutManagePermissionGets403() throws Exception {
        ApprovalDefinitionRequest body = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL,
                CommentScope.PER_STEP,
                LocalDate.now(),
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER)));

        mvc.perform(put("/api/v1/approval-definitions/LEAVE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(hrSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET with core.approval_definition.manage gets 200")
    void getWithManagePermissionGets200() throws Exception {
        mvc.perform(get("/api/v1/approval-definitions")
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT with core.approval_definition.manage gets 200")
    void putWithManagePermissionGets200() throws Exception {
        ApprovalDefinitionRequest body = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL,
                CommentScope.PER_STEP,
                LocalDate.now(),
                List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER)));

        mvc.perform(put("/api/v1/approval-definitions/LEAVE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT of a PROJECT_MANAGER step that is not per_item gets 400, and says why (W-42.2)")
    void putProjectManagerStepNotPerItemGets400() throws Exception {
        ApprovalDefinitionRequest body = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL,
                CommentScope.PER_STEP,
                LocalDate.now(),
                List.of(new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, false)));

        mvc.perform(put("/api/v1/approval-definitions/TIMESHEET")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Step at index 0: PROJECT_MANAGER must be per_item")));
    }

    @Test
    @DisplayName("PUT of a per_item PROJECT_MANAGER step gets 200 (W-42.2)")
    void putProjectManagerStepPerItemGets200() throws Exception {
        ApprovalDefinitionRequest body = new ApprovalDefinitionRequest(
                StepOrdering.SEQUENTIAL,
                CommentScope.PER_STEP,
                LocalDate.now(),
                List.of(new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true)));

        mvc.perform(put("/api/v1/approval-definitions/TIMESHEET")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(jwt().jwt(b -> b.subject(adminSub.toString()).claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
