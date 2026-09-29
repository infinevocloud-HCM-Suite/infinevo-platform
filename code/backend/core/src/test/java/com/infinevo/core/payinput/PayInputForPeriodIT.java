package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * W-19 §7 — {@code forPeriod} is the batch read the pay run needs: every employee's rows for a
 * period in one statement, not one query per employee ({@code QueryCountIT}'s style, per W-55).
 */
@SpringBootTest(classes = PayInputTestApp.class)
@TestPropertySource(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.infinevo.core.payinput.PayInputForPeriodIT$QueryCounter")
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputForPeriodIT extends AbstractIntegrationTest {

    public static class QueryCounter implements StatementInspector {
        public static final AtomicInteger COUNT = new AtomicInteger(0);

        @Override
        public String inspect(String sql) {
            if (sql != null && sql.trim().regionMatches(true, 0, "select", 0, 6)) {
                COUNT.incrementAndGet();
            }
            return sql;
        }
    }

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("ForPeriod " + UUID.randomUUID());
        TenantContext.set(tenant);
        QueryCounter.COUNT.set(0);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Three employees' rows for a period are all returned in one SELECT")
    void forPeriodReadsEveryEmployeeInOneStatement() throws SQLException {
        YearMonth period = YearMonth.of(2026, 4);
        UUID employeeA = PayInputTestSchema.insertEmployee(tenant, "FP-A-" + UUID.randomUUID());
        UUID employeeB = PayInputTestSchema.insertEmployee(tenant, "FP-B-" + UUID.randomUUID());
        UUID employeeC = PayInputTestSchema.insertEmployee(tenant, "FP-C-" + UUID.randomUUID());
        payInputService.record(
                new PayInputCommand(employeeA, period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "a"));
        payInputService.record(
                new PayInputCommand(employeeB, period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "b"));
        payInputService.record(
                new PayInputCommand(employeeC, period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "c"));

        QueryCounter.COUNT.set(0);
        PayInputListResponse response = payInputService.forPeriod(period);

        assertThat(response.rows())
                .extracting(PayInputResponse::employeeId)
                .containsExactlyInAnyOrder(employeeA, employeeB, employeeC);
        assertThat(QueryCounter.COUNT.get()).isEqualTo(1);
    }
}
