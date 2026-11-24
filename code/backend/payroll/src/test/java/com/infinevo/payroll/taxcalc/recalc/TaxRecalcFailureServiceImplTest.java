package com.infinevo.payroll.taxcalc.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/** D-147: recording, listing and retrying failed tax recalculations. */
class TaxRecalcFailureServiceImplTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID officerId = UUID.randomUUID();
    private final String fy = "2026-2027";

    private TaxRecalcFailureRepository repository;
    private TaxRecalculationService recalculationService;
    private TaxRecalcFailureServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(TaxRecalcFailureRepository.class);
        recalculationService = mock(TaxRecalculationService.class);
        service = new TaxRecalcFailureServiceImpl(
                repository, recalculationService, mock(PlatformTransactionManager.class));
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A first failure inserts an open row with the root reason")
    void firstFailureInserts() {
        given(repository.findOpenForUpdate(tenantId, employeeId, fy)).willReturn(Optional.empty());

        service.recordFailure(
                tenantId,
                employeeId,
                fy,
                TaxTrigger.DECLARATION_SUBMITTED,
                new IllegalStateException("wrapped", new IllegalArgumentException("taxable > gross")));

        ArgumentCaptor<TaxRecalcFailure> saved = ArgumentCaptor.forClass(TaxRecalcFailure.class);
        verify(repository).saveAndFlush(saved.capture());
        TaxRecalcFailure row = saved.getValue();
        assertThat(row.getTenantId()).isEqualTo(tenantId);
        assertThat(row.getEmployeeId()).isEqualTo(employeeId);
        assertThat(row.getFinancialYear()).isEqualTo(fy);
        assertThat(row.getTrigger()).isEqualTo(TaxTrigger.DECLARATION_SUBMITTED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getErrorMessage()).contains("wrapped").contains("taxable > gross");
        assertThat(TenantContext.require()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("A repeat failure updates the open row: attempts go up, the latest message wins")
    void repeatFailureUpdates() {
        TaxRecalcFailure open =
                new TaxRecalcFailure(tenantId, employeeId, fy, TaxTrigger.SALARY_REVISION, "first", Instant.now());
        given(repository.findOpenForUpdate(tenantId, employeeId, fy)).willReturn(Optional.of(open));

        service.recordFailure(tenantId, employeeId, fy, TaxTrigger.PROOF_VERIFIED, new RuntimeException("second"));

        verify(repository).saveAndFlush(open);
        assertThat(open.getAttempts()).isEqualTo(2);
        assertThat(open.getTrigger()).isEqualTo(TaxTrigger.PROOF_VERIFIED);
        assertThat(open.getErrorMessage()).contains("second");
    }

    @Test
    @DisplayName("recordFailure never throws, and restores no tenant when none was bound")
    void recordFailureNeverThrows() {
        TenantContext.clear();
        given(repository.findOpenForUpdate(tenantId, employeeId, fy)).willThrow(new RuntimeException("db down"));

        service.recordFailure(tenantId, employeeId, fy, TaxTrigger.OFFICER, new RuntimeException("x"));

        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("A long message is cut to the stored length")
    void longMessageTrimmed() {
        String message = "x".repeat(TaxRecalcFailure.MAX_MESSAGE_LENGTH + 500);
        assertThat(TaxRecalcFailureServiceImpl.describe(new RuntimeException(message)))
                .hasSize(TaxRecalcFailure.MAX_MESSAGE_LENGTH);
    }

    @Test
    @DisplayName("open lists the bound tenant's open failures, for one year or all")
    void openLists() {
        TaxRecalcFailure row =
                new TaxRecalcFailure(tenantId, employeeId, fy, TaxTrigger.SALARY_DEFAULT, "boom", Instant.now());
        given(repository.findOpenForYear(tenantId, fy)).willReturn(List.of(row));
        given(repository.findOpen(tenantId)).willReturn(List.of(row, row));

        assertThat(service.open("2026-2027")).hasSize(1);
        assertThat(service.open(null)).hasSize(2);
        assertThat(service.open("2026-2027").get(0).errorMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("retry of an unknown or closed failure is not found")
    void retryNotFound() {
        UUID id = UUID.randomUUID();
        given(repository.findOpenById(tenantId, id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(id, officerId)).isInstanceOf(TaxRecalcFailureNotFoundException.class);
        verify(recalculationService, never()).recalculate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("retry re-runs the recalculation with the failure's trigger and the officer as author")
    void retrySucceeds() {
        UUID id = UUID.randomUUID();
        TaxRecalcFailure row =
                new TaxRecalcFailure(tenantId, employeeId, fy, TaxTrigger.DECLARATION_SUBMITTED, "x", Instant.now());
        given(repository.findOpenById(tenantId, id)).willReturn(Optional.of(row));
        TaxComputationRecord record = sampleRecord();
        given(recalculationService.recalculate(employeeId, fy, TaxTrigger.DECLARATION_SUBMITTED, officerId))
                .willReturn(record);

        assertThat(service.retry(id, officerId)).isSameAs(record);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("retry that fails again updates the failure row and reports 422")
    void retryFailsAgain() {
        UUID id = UUID.randomUUID();
        TaxRecalcFailure row =
                new TaxRecalcFailure(tenantId, employeeId, fy, TaxTrigger.DECLARATION_SUBMITTED, "x", Instant.now());
        given(repository.findOpenById(tenantId, id)).willReturn(Optional.of(row));
        given(repository.findOpenForUpdate(tenantId, employeeId, fy)).willReturn(Optional.of(row));
        given(recalculationService.recalculate(employeeId, fy, TaxTrigger.DECLARATION_SUBMITTED, officerId))
                .willThrow(new RuntimeException("still broken"));

        assertThatThrownBy(() -> service.retry(id, officerId))
                .isInstanceOf(TaxRecalcRetryFailedException.class)
                .hasMessageContaining("still broken");
        assertThat(row.getAttempts()).isEqualTo(2);
        assertThat(TenantContext.require()).isEqualTo(tenantId);
    }

    private TaxComputationRecord sampleRecord() {
        return new TaxComputationRecord(
                tenantId,
                employeeId,
                null,
                fy,
                "NEW",
                TaxTrigger.DECLARATION_SUBMITTED,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "{}",
                Instant.now(),
                officerId,
                "system");
    }
}
