package com.infinevo.worker.subscription;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.subscription.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SubscriptionOverdueJobTest {

    private JdbcTemplate jdbcTemplate;
    private SubscriptionService subscriptionService;
    private SubscriptionOverdueJob job;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        subscriptionService = mock(SubscriptionService.class);
        job = new SubscriptionOverdueJob(jdbcTemplate, subscriptionService);
    }

    @Test
    @DisplayName("W-86: One tenant throwing an exception does not halt processing of subsequent tenants")
    @SuppressWarnings("unchecked")
    void execute_whenOneTenantThrows_continuesToNextTenant() {
        LocalDate asOf = LocalDate.of(2026, 11, 23);
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        when(jdbcTemplate.query(
                        eq("SELECT tenant_id FROM core.list_overdue_subscriptions(?)"),
                        any(RowMapper.class),
                        eq(java.sql.Date.valueOf(asOf))))
                .thenReturn(List.of(tenant1, tenant2));

        doThrow(new RuntimeException("Simulated tenant failure"))
                .when(subscriptionService)
                .markPastDue(tenant1, asOf);

        job.execute(asOf);

        verify(subscriptionService, times(1)).markPastDue(tenant1, asOf);
        verify(subscriptionService, times(1)).markPastDue(tenant2, asOf);
    }
}
