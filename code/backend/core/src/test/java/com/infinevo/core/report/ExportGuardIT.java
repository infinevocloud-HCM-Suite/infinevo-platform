package com.infinevo.core.report;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-23.1 spec section 7 — {@code POST /exports} is {@code 403} without {@code core.report.read}, and
 * {@code 403} with it but without the definition's {@code required_action}; {@code GET
 * /report-definitions} omits that definition; changing definitions needs {@code core.report.manage}.
 *
 * <p>Through the real chain: tenant filter, the permission aspect, the roles in the database.
 */
@SpringBootTest(classes = ReportTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            ReportTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ExportGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID admin;
    private UUID employee;
    private UUID readerOnly;
    private UUID employeeExporter;

    @BeforeEach
    void seed() throws Exception {
        tenant = ReportTestSchema.insertTenant("Guard " + UUID.randomUUID());
        ReportTestSchema.insertRole(tenant, "report-reader", "core.report.read");
        ReportTestSchema.insertRole(tenant, "employee-exporter", "core.report.read", "core.employee.export");
        admin = member("tenant-admin");
        employee = member("employee");
        readerOnly = member("report-reader");
        employeeExporter = member("employee-exporter");
        ReportTestSchema.insertEmployee(tenant, "E-1", "ACTIVE");
    }

    @Test
    @DisplayName("employee: 403 on export - no core.report.read")
    void noReportReadIsRefused() throws Exception {
        mvc.perform(as(employee, post("/api/v1/exports")).content(exportBody("employees")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.report.read")));
    }

    @Test
    @DisplayName("core.report.read alone: 403 on the employees export - the definition's own action is missing")
    void requiredActionIsTheSecondGate() throws Exception {
        mvc.perform(as(readerOnly, post("/api/v1/exports")).content(exportBody("employees")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.employee.export")));
    }

    @Test
    @DisplayName("The list shows each caller only what they could run")
    void listHidesWhatCannotBeRun() throws Exception {
        mvc.perform(as(readerOnly, get("/api/v1/report-definitions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(employeeExporter, get("/api/v1/report-definitions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("employees")))
                .andExpect(jsonPath("$[*].code").value(not(hasItem("audit-log"))));
    }

    @Test
    @DisplayName(
            "hr: V040's grant of core.report.read, with the actions hr already holds, runs all three seeded exports")
    void hrRunsTheSeededExports() throws Exception {
        UUID hr = member("hr");

        mvc.perform(as(hr, get("/api/v1/report-definitions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("employees")))
                .andExpect(jsonPath("$[*].code").value(hasItem("org-masters")))
                .andExpect(jsonPath("$[*].code").value(hasItem("audit-log")));
        mvc.perform(as(hr, post("/api/v1/exports")).content(exportBody("employees")))
                .andExpect(status().isCreated());
        mvc.perform(as(hr, post("/api/v1/report-definitions"))
                        .content("{\"code\":\"x\",\"name\":\"X\",\"source\":\"employee\",\"columns\":[\"status\"],"
                                + "\"format\":\"CSV\",\"requiredAction\":\"core.employee.export\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("With both actions the export goes through: 201, a document and a link")
    void bothActionsExport() throws Exception {
        mvc.perform(as(employeeExporter, post("/api/v1/exports")).content(exportBody("employees")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.format").value("XLSX"))
                .andExpect(jsonPath("$.url").value(containsString("/api/v1/documents/download?t=")));
    }

    @Test
    @DisplayName("Changing definitions needs core.report.manage; a system definition is 409 even for the admin")
    void manageIsRequiredAndSystemIsReadOnly() throws Exception {
        String body = "{\"code\":\"leavers\",\"name\":\"Leavers\",\"source\":\"employee\","
                + "\"columns\":[\"employee_number\",\"termination_date\"],"
                + "\"defaultFilters\":{\"status\":\"TERMINATED\"},\"format\":\"CSV\","
                + "\"requiredAction\":\"core.employee.export\"}";

        mvc.perform(as(employeeExporter, post("/api/v1/report-definitions")).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(as(admin, post("/api/v1/report-definitions")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("leavers"));

        UUID seeded = ReportTestSchema.definitionId(tenant, "employees");
        mvc.perform(as(employeeExporter, put("/api/v1/report-definitions/" + seeded))
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(as(admin, put("/api/v1/report-definitions/" + seeded)).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A definition with a column outside its source's allow-list is refused 400")
    void unknownColumnIsRefused() throws Exception {
        String body = "{\"code\":\"sneaky\",\"name\":\"Sneaky\",\"source\":\"employee\","
                + "\"columns\":[\"employee_number\",\"bank_account_number\"],\"format\":\"CSV\","
                + "\"requiredAction\":\"core.employee.export\"}";

        mvc.perform(as(admin, post("/api/v1/report-definitions")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.columns").value(containsString("bank_account_number")));
    }

    private UUID member(String role) throws Exception {
        UUID sub = UUID.randomUUID();
        ReportTestSchema.insertMember(tenant, sub, role);
        return sub;
    }

    private String exportBody(String code) throws Exception {
        return "{\"definitionId\":\"" + ReportTestSchema.definitionId(tenant, code) + "\"}";
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }
}
