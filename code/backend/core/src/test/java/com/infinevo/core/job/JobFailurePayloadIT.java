package com.infinevo.core.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-73.7 merge review: a failed bulk import job must not keep the uploaded file — names and emails — in
 * {@code core.job_status.result_payload}. {@code markFailed} cuts an {@code import} job's payload to its kind,
 * and leaves every other queue's payload as it was. Through the real service, as {@code app_user} under RLS.
 */
@SpringBootTest(classes = JobFailurePayloadIT.TestApp.class)
class JobFailurePayloadIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {}

    /** JSON-escaped, as Jackson writes the job input: backslash-n inside the string, not a raw newline. */
    private static final String CSV = "employee_number,first_name\\nE1,Asha\\n";

    @Autowired
    private JobService jobService;

    private UUID tenant;

    private static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    /** The two scripts this needs, each only if absent: the container is shared by every IT in the module. */
    @BeforeAll
    static void applyMigrations() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                execute(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "job_status")) {
                execute(conn, "db/migration/core/V006__job_status_and_shedlock.sql");
            }
        }
    }

    @BeforeEach
    void seed() throws SQLException {
        tenant = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "JobFailure " + tenant);
            ps.executeUpdate();
        }
        TenantContext.set(tenant);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a failed import job keeps only its kind; the uploaded file is gone")
    void failedImportDropsTheFile() throws SQLException {
        String jobId = "import-" + UUID.randomUUID();
        jobService.createJob(
                jobId,
                tenant,
                "import",
                "{\"kind\":\"IMPORT\",\"actorUserId\":\"" + UUID.randomUUID() + "\",\"csv\":\"" + CSV + "\"}");

        jobService.markFailed(jobId, "boom");

        assertThat(payload(jobId)).isEqualTo("{\"kind\":\"IMPORT\"}");
        assertThat(jobService.getJobStatus(jobId, tenant).orElseThrow().status())
                .isEqualTo(JobState.FAILED);
    }

    @Test
    @DisplayName("a failed pay run keeps its payload, as before")
    void failedPayRunKeepsItsPayload() throws SQLException {
        String jobId = "payrun-" + UUID.randomUUID();
        String payrunPayload = "{\"payrunId\":\"" + UUID.randomUUID() + "\",\"attempt\":1}";
        jobService.createJob(jobId, tenant, "payrun", payrunPayload);

        jobService.markFailed(jobId, "boom");

        assertThat(payload(jobId)).isEqualTo(payrunPayload);
    }

    private static String payload(String jobId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT result_payload FROM core.job_status WHERE job_id = ?")) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void execute(Connection conn, String resource) throws Exception {
        try (InputStream is = JobFailurePayloadIT.class.getClassLoader().getResourceAsStream(resource);
                Statement stmt = conn.createStatement()) {
            stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
