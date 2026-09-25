package com.infinevo.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = JobStatusGuardIT.TestApp.class)
@AutoConfigureMockMvc
class JobStatusGuardIT extends AbstractIntegrationTest {

    @SpringBootApplication(
            scanBasePackages = {"com.infinevo.app", "com.infinevo.shared"},
            exclude = {FlywayAutoConfiguration.class})
    static class TestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("Returns 403 Forbidden when caller lacks core.job.read permission")
    @WithMockUser(username = USER_ID)
    void forbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/job-100").header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns 404 Not Found when requested job belongs to another tenant or not found")
    @WithMockUser(username = USER_ID, authorities = "core.job.read")
    void notFoundForOtherTenantJob() throws Exception {
        BDDMockito.given(jobService.getJobStatus("job-other", TENANT_A)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/job-other").header("X-Tenant-Id", TENANT_A.toString()))
                .andExpect(status().isNotFound());
    }
}
