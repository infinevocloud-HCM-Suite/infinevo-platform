package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProofAmountReaderTest {

    private EmployeeProofOfInvestmentRepository proofRepository;
    private EmployeeProofItemRepository itemRepository;
    private EmployeeInvHouseRentRepository houseRentRepository;
    private ProofAmountReader reader;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID proofId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        proofRepository = mock(EmployeeProofOfInvestmentRepository.class);
        itemRepository = mock(EmployeeProofItemRepository.class);
        houseRentRepository = mock(EmployeeInvHouseRentRepository.class);
        reader = new ProofAmountReaderImpl(proofRepository, itemRepository, houseRentRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("approvedAmounts returns empty map when proof does not exist")
    void approvedAmounts_whenProofDoesNotExist_returnsEmpty() {
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.empty());

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("approvedAmounts returns empty map when proof status is DRAFT")
    void approvedAmounts_whenProofDraft_returnsEmpty() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.DRAFT);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("approvedAmounts returns empty map when proof status is SUBMITTED")
    void approvedAmounts_whenProofSubmitted_returnsEmpty() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("approvedAmounts returns empty map when proof status is REJECTED")
    void approvedAmounts_whenProofRejected_returnsEmpty() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.REJECTED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("approvedAmounts returns empty map when proof has no items")
    void approvedAmounts_whenProofHasNoItems_returnsEmpty() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(Collections.emptyList());

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("approvedAmounts returns approved amounts for non-house-rent items with scale 4")
    void approvedAmounts_whenProofApproved_returnsApprovedAmountsForNonHouseRent() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        UUID line1 = UUID.randomUUID();
        UUID line2 = UUID.randomUUID();
        UUID line3 = UUID.randomUUID();
        UUID line4 = UUID.randomUUID();

        EmployeeProofItem item1 = createItem(ProofSourceKind.SECTION_6A, line1, new BigDecimal("150000.0000"));
        EmployeeProofItem item2 = createItem(ProofSourceKind.HOME_LOAN_PRINCIPAL, line2, new BigDecimal("200000.0000"));
        EmployeeProofItem item3 = createItem(ProofSourceKind.HOME_LOAN_INTEREST, line2, new BigDecimal("75000.0000"));
        EmployeeProofItem item4 = createItem(ProofSourceKind.LET_OUT_PROPERTY, line3, new BigDecimal("50000.0000"));
        EmployeeProofItem item5 = createItem(ProofSourceKind.PREV_EMPLOYMENT, line4, new BigDecimal("35000.0000"));

        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1, item2, item3, item4, item5));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).hasSize(5);
        assertThat(amounts.get(Pair.of(ProofSourceKind.SECTION_6A, line1)))
                .isEqualByComparingTo(new BigDecimal("150000.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.HOME_LOAN_PRINCIPAL, line2)))
                .isEqualByComparingTo(new BigDecimal("200000.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.HOME_LOAN_INTEREST, line2)))
                .isEqualByComparingTo(new BigDecimal("75000.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.LET_OUT_PROPERTY, line3)))
                .isEqualByComparingTo(new BigDecimal("50000.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.PREV_EMPLOYMENT, line4)))
                .isEqualByComparingTo(new BigDecimal("35000.0000"));
    }

    @Test
    @DisplayName("approvedAmounts spreads house rent approved amount over rental months (scale 4, HALF_UP)")
    void approvedAmounts_whenProofApproved_spreadsHouseRentOverMonths() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        UUID rent1Id = UUID.randomUUID();
        UUID rent2Id = UUID.randomUUID();

        // 12 months: April 2026 to March 2027. Approved: 120,000 -> 10,000.0000 per month
        EmployeeProofItem rentItem1 = createItem(ProofSourceKind.HOUSE_RENT, rent1Id, new BigDecimal("120000.0000"));
        // 3 months: April 2026 to June 2026. Approved: 50,000 -> 16,666.6667 per month
        EmployeeProofItem rentItem2 = createItem(ProofSourceKind.HOUSE_RENT, rent2Id, new BigDecimal("50000.0000"));

        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(rentItem1, rentItem2));

        EmployeeInvHouseRent rent1 =
                createRent(rent1Id, LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), new BigDecimal("15000.0000"));
        EmployeeInvHouseRent rent2 =
                createRent(rent2Id, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), new BigDecimal("20000.0000"));

        when(houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId))
                .thenReturn(List.of(rent1, rent2));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).hasSize(2);
        assertThat(amounts.get(Pair.of(ProofSourceKind.HOUSE_RENT, rent1Id)))
                .isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.HOUSE_RENT, rent2Id)))
                .isEqualByComparingTo(new BigDecimal("16666.6667"));
    }

    @Test
    @DisplayName("approvedAmounts returns zero scale 4 when item approved amount is null or zero (disallowed)")
    void approvedAmounts_whenItemApprovedAmountNullOrZero_returnsZeroScale4() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        UUID line1 = UUID.randomUUID();
        UUID line2 = UUID.randomUUID();

        EmployeeProofItem item1 = createItem(ProofSourceKind.SECTION_6A, line1, null);
        EmployeeProofItem item2 = createItem(ProofSourceKind.SECTION_6A, line2, BigDecimal.ZERO);

        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1, item2));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).hasSize(2);
        assertThat(amounts.get(Pair.of(ProofSourceKind.SECTION_6A, line1)))
                .isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(amounts.get(Pair.of(ProofSourceKind.SECTION_6A, line2)))
                .isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("approvedAmounts falls back to approved amount if house rent row not found in repo")
    void approvedAmounts_whenHouseRentNotFound_fallsBackToApprovedAmount() {
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        UUID rentId = UUID.randomUUID();
        EmployeeProofItem rentItem = createItem(ProofSourceKind.HOUSE_RENT, rentId, new BigDecimal("25000.0000"));
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(rentItem));

        when(houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId))
                .thenReturn(Collections.emptyList());

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(tenantId, declarationId);

        assertThat(amounts).hasSize(1);
        assertThat(amounts.get(Pair.of(ProofSourceKind.HOUSE_RENT, rentId)))
                .isEqualByComparingTo(new BigDecimal("25000.0000"));
    }

    @Test
    @DisplayName("approvedAmounts resolves tenant from TenantContext when single-arg method is used")
    void approvedAmounts_resolvesTenantFromContext() {
        TenantContext.set(tenantId);
        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED);
        when(proofRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(Optional.of(proof));

        UUID line1 = UUID.randomUUID();
        EmployeeProofItem item1 = createItem(ProofSourceKind.SECTION_6A, line1, new BigDecimal("80000.0000"));
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1));

        Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts = reader.approvedAmounts(declarationId);

        assertThat(amounts).hasSize(1);
        assertThat(amounts.get(Pair.of(ProofSourceKind.SECTION_6A, line1)))
                .isEqualByComparingTo(new BigDecimal("80000.0000"));
    }

    @Test
    @DisplayName("approvedAmounts throws IllegalStateException when TenantContext is unbound")
    void approvedAmounts_whenTenantUnbound_throwsIllegalState() {
        TenantContext.clear();
        assertThatThrownBy(() -> reader.approvedAmounts(declarationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    @DisplayName("Pair record provides expected equality and accessors")
    void pairRecord_equalityAndAccessors() {
        UUID id = UUID.randomUUID();
        Pair<ProofSourceKind, UUID> p1 = Pair.of(ProofSourceKind.HOUSE_RENT, id);
        Pair<ProofSourceKind, UUID> p2 = new Pair<>(ProofSourceKind.HOUSE_RENT, id);
        Pair<ProofSourceKind, UUID> p3 = Pair.of(ProofSourceKind.SECTION_6A, id);

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1).isNotEqualTo(p3);
        assertThat(p1.first()).isEqualTo(ProofSourceKind.HOUSE_RENT);
        assertThat(p1.second()).isEqualTo(id);
        assertThat(p1.getFirst()).isEqualTo(ProofSourceKind.HOUSE_RENT);
        assertThat(p1.getSecond()).isEqualTo(id);
    }

    private EmployeeProofOfInvestment createProof(ProofStatus status) {
        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(tenantId, employeeId, declarationId, "2026-2027", "actor");
        ReflectionTestUtils.setField(proof, "id", proofId);
        ReflectionTestUtils.setField(proof, "status", status);
        return proof;
    }

    private EmployeeProofItem createItem(ProofSourceKind kind, UUID sourceLineId, BigDecimal approvedAmount) {
        EmployeeProofItem item = new EmployeeProofItem(
                tenantId, proofId, kind, sourceLineId, "Test item", new BigDecimal("100000.0000"), "actor");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(item, "approvedAmount", approvedAmount);
        return item;
    }

    private EmployeeInvHouseRent createRent(
            UUID rentId, LocalDate fromMonth, LocalDate toMonth, BigDecimal amountPerMonth) {
        EmployeeInvHouseRent rent = new EmployeeInvHouseRent(
                tenantId, declarationId, fromMonth, toMonth, "Address", "Landlord", "PAN123456", true, amountPerMonth);
        rent.setId(rentId);
        return rent;
    }
}
