package com.infinevo.shared.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W-04.1 — the connection budget of the shared test container.
 *
 * <p>Every cached Spring test context holds a live HikariCP pool against the one Postgres
 * container. When contexts × pool size outgrows {@code max_connections}, the last test class to
 * open a connection fails with {@code 53300 too_many_connections} for reasons that say nothing
 * about the test. This class fails first, and names the three numbers.
 *
 * <p>The context count is taken from the source tree: every file under {@code src/test/java}
 * mentioning the annotation counts as one context. That over-counts slightly (a javadoc mention,
 * two classes sharing a configuration), which errs on the safe side.
 */
@SpringBootTest(
        classes = ConnectionBudgetIT.TestApp.class,
        properties = "spring.datasource.hikari.data-source-properties.ApplicationName=" + ConnectionBudgetIT.APP_NAME)
class ConnectionBudgetIT extends AbstractIntegrationTest {

    /** Tags this context's connections, so other cached contexts' pools are not counted. */
    static final String APP_NAME = "connection-budget-it";

    /**
     * The pools may claim at most this share of the container's slots. The rest is headroom for
     * raw {@code DriverManager} connections tests open themselves, for Flyway, and for the next
     * context somebody adds. Half is what makes the guard bite: at the old default pool of 10,
     * 18 contexts x 10 = 180 is over 100 and fails, while the corrected pool of 2 gives 36.
     */
    private static final int POOL_SHARE_PERCENT = 50;

    private static final String ANNOTATION = "@SpringBootTest";

    @SpringBootApplication
    static class TestApp {
        // Minimal context: a DataSource and a JdbcTemplate from autoconfiguration.
    }

    /** The tenant-binding proxy over Hikari; {@link #poolSize()} unwraps it. */
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("contexts x pool size + headroom fits in the container's max_connections")
    void budgetFits() throws IOException, SQLException {
        Path testSources = Path.of("src", "test", "java");
        assumeTrue(
                Files.isDirectory(testSources),
                "test sources not found relative to " + Path.of("").toAbsolutePath());

        long contexts;
        try (Stream<Path> files = Files.walk(testSources)) {
            contexts = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(ConnectionBudgetIT::mentionsSpringBootTest)
                    .count();
        }
        int pool = poolSize();
        int maxConnections = Integer.parseInt(jdbc.queryForObject("SHOW max_connections", String.class));

        long needed = contexts * pool;
        long allowed = maxConnections * POOL_SHARE_PERCENT / 100;
        assertThat(needed)
                .as(
                        "connection budget: %d @SpringBootTest contexts x pool size %d = %d, "
                                + "but the pools may use at most %d%% of max_connections = %d, i.e. %d",
                        contexts, pool, needed, POOL_SHARE_PERCENT, maxConnections, allowed)
                .isLessThanOrEqualTo(allowed);
    }

    @Test
    @DisplayName("50 concurrent queries never hold more app_user connections than the pool")
    void burstStaysInsidePool() throws Exception {
        int pool = poolSize();
        // Four times the pool in flight at once, so the pool is what bounds the count, not the caller.
        ExecutorService pool4x = Executors.newFixedThreadPool(pool * 4);
        AtomicInteger peak = new AtomicInteger();
        try {
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                work.add(pool4x.submit(() -> {
                    // pg_sleep holds the connection long enough for the others to pile up behind it.
                    jdbc.queryForObject("SELECT pg_sleep(0.05)", String.class);
                    Integer open = jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE usename = ? AND application_name = ?",
                            Integer.class,
                            PostgresTestContainerInitializer.APP_USER,
                            APP_NAME);
                    peak.accumulateAndGet(open, Math::max);
                }));
            }
            for (Future<?> f : work) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool4x.shutdownNow();
        }
        assertThat(peak.get())
                .as("peak app_user connections from this context during the burst (pool size %d)", pool)
                .isBetween(2, pool);
    }

    private int poolSize() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
    }

    private static boolean mentionsSpringBootTest(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(ANNOTATION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
