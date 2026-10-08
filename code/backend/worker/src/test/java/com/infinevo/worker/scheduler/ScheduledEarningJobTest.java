package com.infinevo.worker.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.scheduled.ScheduledEarningService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** W-73.6: the nightly job binds each tenant for its own call and carries on past one that fails. */
class ScheduledEarningJobTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 11);
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ScheduledEarningService service = mock(ScheduledEarningService.class);
    private final ScheduledEarningJob job = new ScheduledEarningJob(jdbcTemplate, service);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Every tenant is materialised under its own binding, and the tenant is cleared after")
    @SuppressWarnings("unchecked")
    void eachTenantBoundInTurn() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(TENANT_A, TENANT_B));
        List<UUID> seen = new ArrayList<>();
        when(service.materialise(PERIOD)).thenAnswer(inv -> {
            seen.add(TenantContext.require());
            return seen.size() == 1 ? 2 : 1;
        });

        int written = job.execute(PERIOD);

        assertThat(seen).containsExactly(TENANT_A, TENANT_B);
        assertThat(written).isEqualTo(3);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    @DisplayName("One tenant's failure is logged and the next tenant still runs")
    @SuppressWarnings("unchecked")
    void failureDoesNotStopTheSweep() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(TENANT_A, TENANT_B));
        when(service.materialise(PERIOD))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(1);

        int written = job.execute(PERIOD);

        verify(service, times(2)).materialise(PERIOD);
        assertThat(written).isEqualTo(1);
        assertThat(TenantContext.current()).isEmpty();
    }
}
