package com.infinevo.payroll.challan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TdsChallanServiceImpl} (W-89.1).
 */
class TdsChallanServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TdsChallanRepository challanRepository;
    private TdsChallanAllocationRepository allocationRepository;
    private TdsChallanServiceImpl service;

    @BeforeEach
    void setUp() {
        challanRepository = mock(TdsChallanRepository.class);
        allocationRepository = mock(TdsChallanAllocationRepository.class);
        service = new TdsChallanServiceImpl(challanRepository, allocationRepository);

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create valid challan succeeds and calculates total amount correctly")
    void create_validChallan_succeeds() {
        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                "July TDS payment");

        when(challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(any(), any(), any(), any()))
                .thenReturn(false);
        when(challanRepository.save(any(TdsChallan.class))).thenAnswer(inv -> {
            TdsChallan c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        TdsChallanResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.financialYear()).isEqualTo("2026-2027");
        assertThat(response.quarter()).isEqualTo("Q1");
        assertThat(response.bsrCode()).isEqualTo("0210045");
        assertThat(response.taxAmount()).isEqualByComparingTo("50000.00");
        assertThat(response.cessAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("52000.00");
        assertThat(response.unallocatedAmount()).isEqualByComparingTo("52000.00");
        assertThat(response.filed()).isFalse();
    }

    @Test
    @DisplayName("Create with invalid BSR code (not 7 digits) throws validation exception")
    void create_invalidBsrCode_throwsException() {
        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "123", // only 3 digits
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("BSR code must be exactly 7 digits");
    }

    @Test
    @DisplayName("Create with duplicate challan throws validation exception")
    void create_duplicateChallan_throwsException() {
        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                null);

        when(challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(
                        TENANT_ID, "0210045", "12345", LocalDate.of(2026, 7, 5)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Updating a filed challan throws validation exception")
    void update_filedChallan_throwsException() {
        UUID challanId = UUID.randomUUID();
        TdsChallan existing = new TdsChallan();
        existing.setId(challanId);
        existing.setTenantId(TENANT_ID);
        existing.setFiled(true);

        when(challanRepository.findByTenantIdAndId(TENANT_ID, challanId)).thenReturn(Optional.of(existing));

        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                null);

        assertThatThrownBy(() -> service.update(challanId, request))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("Filed challans cannot be modified");
    }

    @Test
    @DisplayName("Deleting a filed challan throws validation exception")
    void delete_filedChallan_throwsException() {
        UUID challanId = UUID.randomUUID();
        TdsChallan existing = new TdsChallan();
        existing.setId(challanId);
        existing.setTenantId(TENANT_ID);
        existing.setFiled(true);

        when(challanRepository.findByTenantIdAndId(TENANT_ID, challanId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(challanId))
                .isInstanceOf(TdsChallanValidationException.class)
                .hasMessageContaining("Filed challans cannot be deleted");
    }

    @Test
    @DisplayName("Allocating funds to pay run deductees calculates unallocated amount")
    void allocate_funds_updatesAllocatedFigures() {
        UUID challanId = UUID.randomUUID();
        TdsChallan existing = new TdsChallan();
        existing.setId(challanId);
        existing.setTenantId(TENANT_ID);
        existing.setTotalAmount(new BigDecimal("10000.00"));
        existing.setFiled(false);

        when(challanRepository.findByTenantIdAndId(TENANT_ID, challanId)).thenReturn(Optional.of(existing));

        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID();
        UUID payRunId = UUID.randomUUID();

        ChallanAllocationRequest request = new ChallanAllocationRequest(
                payRunId,
                List.of(
                        new ChallanAllocationRequest.EmployeeAllocationItem(
                                emp1, new BigDecimal("4000.00"), BigDecimal.ZERO),
                        new ChallanAllocationRequest.EmployeeAllocationItem(
                                emp2, new BigDecimal("3000.00"), BigDecimal.ZERO)));

        TdsChallanAllocation a1 = new TdsChallanAllocation();
        a1.setAllocatedTax(new BigDecimal("4000.00"));
        a1.setAllocatedCess(BigDecimal.ZERO);

        TdsChallanAllocation a2 = new TdsChallanAllocation();
        a2.setAllocatedTax(new BigDecimal("3000.00"));
        a2.setAllocatedCess(BigDecimal.ZERO);

        when(allocationRepository.findByTenantIdAndChallanId(TENANT_ID, challanId))
                .thenReturn(List.of(a1, a2));

        TdsChallanResponse response = service.allocate(challanId, request);

        assertThat(response).isNotNull();
        assertThat(response.allocatedAmount()).isEqualByComparingTo("7000.00");
        assertThat(response.unallocatedAmount()).isEqualByComparingTo("3000.00");
        verify(allocationRepository, org.mockito.Mockito.times(2)).save(any(TdsChallanAllocation.class));
    }
}
