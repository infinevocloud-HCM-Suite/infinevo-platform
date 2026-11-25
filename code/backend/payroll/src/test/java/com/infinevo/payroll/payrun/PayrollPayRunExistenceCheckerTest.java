package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PayrollPayRunExistenceCheckerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PayrollPayRunExistenceChecker checker = new PayrollPayRunExistenceChecker(jdbcTemplate);

    @Test
    @DisplayName("null tenant returns false without querying database")
    void nullTenantReturnsFalse() {
        assertThat(checker.hasProcessedPayRun(null)).isFalse();
    }

    @Test
    @DisplayName("returns true when non-cancelled pay run exists")
    void returnsTrueWhenPayRunExists() {
        UUID tenantId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(tenantId)))
                .thenReturn(1);

        assertThat(checker.hasProcessedPayRun(tenantId)).isTrue();
    }

    @Test
    @DisplayName("returns false when no pay run exists or count is 0")
    void returnsFalseWhenNoPayRun() {
        UUID tenantId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(tenantId)))
                .thenReturn(0);

        assertThat(checker.hasProcessedPayRun(tenantId)).isFalse();
    }
}
