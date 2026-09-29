package com.infinevo.payroll.statutory.pt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.org.WorkLocationService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PtOverrideValidationTest {

    private ReferenceStateRepository referenceStateRepository;
    private OrgPtOverrideRepository orgPtOverrideRepository;
    private ProfessionalTaxService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        WorkLocationService workLocationService = mock(WorkLocationService.class);
        referenceStateRepository = mock(ReferenceStateRepository.class);
        PtStateRepository ptStateRepository = mock(PtStateRepository.class);
        PtSlabRepository ptSlabRepository = mock(PtSlabRepository.class);
        orgPtOverrideRepository = mock(OrgPtOverrideRepository.class);
        OrgPtOverrideSlabRepository orgPtOverrideSlabRepository = mock(OrgPtOverrideSlabRepository.class);
        PtHistoryRepository ptHistoryRepository = mock(PtHistoryRepository.class);

        when(referenceStateRepository.existsById("KA")).thenReturn(true);
        when(referenceStateRepository.existsById("UNKNOWN")).thenReturn(false);

        service = new ProfessionalTaxServiceImpl(
                workLocationService,
                referenceStateRepository,
                ptStateRepository,
                ptSlabRepository,
                orgPtOverrideRepository,
                orgPtOverrideSlabRepository,
                ptHistoryRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Unknown state is refused with 400 validation error")
    void unknownStateRefused() {
        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(new PtSlabDto(BigDecimal.ZERO, null, new BigDecimal("200.0000"), false, null)));

        assertThatThrownBy(() -> service.setOverride("UNKNOWN", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Unknown state code");
    }

    @Test
    @DisplayName("Overlapping slabs are refused")
    void overlappingSlabsRefused() {
        // Slab 1: 0 to 15000, Slab 2: 12000 to null (overlap 12000-15000)
        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(BigDecimal.ZERO, new BigDecimal("15000.0000"), BigDecimal.ZERO, false, null),
                        new PtSlabDto(new BigDecimal("12000.0000"), null, new BigDecimal("200.0000"), false, null)));

        assertThatThrownBy(() -> service.setOverride("KA", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Overlapping slabs");
    }

    @Test
    @DisplayName("Gap between slabs is refused")
    void gapBetweenSlabsRefused() {
        // Slab 1: 0 to 10000, Slab 2: 15000 to null (gap 10000-15000)
        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(BigDecimal.ZERO, new BigDecimal("10000.0000"), BigDecimal.ZERO, false, null),
                        new PtSlabDto(new BigDecimal("15000.0000"), null, new BigDecimal("200.0000"), false, null)));

        assertThatThrownBy(() -> service.setOverride("KA", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Gap between slabs");
    }

    @Test
    @DisplayName("to_amount null before the last slab is refused")
    void toAmountNullBeforeLastRefused() {
        // Slab 1 has to_amount null, but is followed by Slab 2
        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(BigDecimal.ZERO, null, BigDecimal.ZERO, false, null),
                        new PtSlabDto(new BigDecimal("20000.0000"), null, new BigDecimal("200.0000"), false, null)));

        assertThatThrownBy(() -> service.setOverride("KA", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Only the last slab may have to_amount null");
    }

    @Test
    @DisplayName("Month-split override slabs are allowed")
    void monthSplitSlabsAccepted() {
        OrgPtOverride override = new OrgPtOverride(tenantId, "MH", "REG123", LocalDate.of(2025, 4, 1), "system");
        when(referenceStateRepository.existsById("MH")).thenReturn(true);
        when(orgPtOverrideRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(override);
        when(referenceStateRepository.findByCode("MH"))
                .thenReturn(java.util.Optional.of(new ReferenceState("MH", "27", "Maharashtra", "IN", false)));

        // Slabs with month split (months 1, 3..12 vs month 2)
        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(BigDecimal.ZERO, new BigDecimal("7500.0000"), BigDecimal.ZERO, false, null),
                        new PtSlabDto(
                                new BigDecimal("7500.0000"),
                                new BigDecimal("10000.0000"),
                                new BigDecimal("175.0000"),
                                true,
                                null),
                        new PtSlabDto(
                                new BigDecimal("10000.0000"),
                                new BigDecimal("25000.0000"),
                                new BigDecimal("200.0000"),
                                true,
                                List.of(1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                        new PtSlabDto(
                                new BigDecimal("10000.0000"),
                                new BigDecimal("25000.0000"),
                                new BigDecimal("300.0000"),
                                true,
                                List.of(2)),
                        new PtSlabDto(
                                new BigDecimal("25000.0000"),
                                null,
                                new BigDecimal("200.0000"),
                                false,
                                List.of(1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                        new PtSlabDto(
                                new BigDecimal("25000.0000"), null, new BigDecimal("300.0000"), false, List.of(2))));

        PtStateResponse response = service.setOverride("MH", request);
        org.assertj.core.api.Assertions.assertThat(response).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.slabs()).hasSize(6);
    }

    @Test
    @DisplayName("Month-split slabs with overlap in the same month are refused")
    void monthSplitSlabsWithOverlapRefused() {
        when(referenceStateRepository.existsById("MH")).thenReturn(true);

        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(
                                BigDecimal.ZERO, new BigDecimal("15000.0000"), BigDecimal.ZERO, false, List.of(2)),
                        new PtSlabDto(
                                new BigDecimal("10000.0000"), null, new BigDecimal("300.0000"), false, List.of(2))));

        assertThatThrownBy(() -> service.setOverride("MH", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Overlapping slabs");
    }

    @Test
    @DisplayName("Month-split slabs with gap in the same month are refused")
    void monthSplitSlabsWithGapRefused() {
        when(referenceStateRepository.existsById("MH")).thenReturn(true);

        PtOverrideRequest request = new PtOverrideRequest(
                "REG123",
                LocalDate.of(2025, 4, 1),
                List.of(
                        new PtSlabDto(
                                BigDecimal.ZERO, new BigDecimal("10000.0000"), BigDecimal.ZERO, false, List.of(2)),
                        new PtSlabDto(
                                new BigDecimal("15000.0000"), null, new BigDecimal("300.0000"), false, List.of(2))));

        assertThatThrownBy(() -> service.setOverride("MH", request))
                .isInstanceOf(PtValidationException.class)
                .hasMessageContaining("Gap between slabs");
    }
}
