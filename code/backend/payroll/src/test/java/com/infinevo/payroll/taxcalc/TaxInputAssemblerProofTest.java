package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.proof.Pair;
import com.infinevo.payroll.proof.ProofAmountReader;
import com.infinevo.payroll.proof.ProofSourceKind;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLine;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLineRepository;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyLineType;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyRuleReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-34.2 spec section 7, the test the spec calls {@code TaxInputGathererProofTest}: the class it names was
 * built as {@link TaxInputAssembler}, which reads approved figures through {@link ProofAdjustment}.
 *
 * <p>No proof and a {@code SUBMITTED} proof use the declared figures ({@link ProofAmountReader} answers empty
 * for both); an {@code APPROVED} proof uses the approved figure for each kind, house rent spread over its
 * months by the reader. The declaration's own rows are never changed.
 */
class TaxInputAssemblerProofTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DECLARATION = UUID.randomUUID();

    private ProofAmountReader reader;
    private EmployeeInvLetOutPropertyLineRepository lineRepository;
    private LetOutPropertyRuleReader ruleReader;
    private ProofAdjustment adjustment;
    private EmployeeInvestmentDeclaration declaration;

    @BeforeEach
    void setUp() {
        reader = mock(ProofAmountReader.class);
        lineRepository = mock(EmployeeInvLetOutPropertyLineRepository.class);
        ruleReader = mock(LetOutPropertyRuleReader.class);
        adjustment = new ProofAdjustment(reader, lineRepository, ruleReader);
        declaration = new EmployeeInvestmentDeclaration();
        declaration.setId(DECLARATION);
        declaration.setTenantId(TENANT);
        declaration.setFinancialYear("2026-2027");
    }

    private static EmployeeInvHouseRent rent(String perMonth) {
        EmployeeInvHouseRent rent = new EmployeeInvHouseRent();
        rent.setId(UUID.randomUUID());
        rent.setTenantId(TENANT);
        rent.setDeclarationId(DECLARATION);
        rent.setFromMonth(LocalDate.of(2026, 4, 1));
        rent.setToMonth(LocalDate.of(2027, 3, 1));
        rent.setMetro(true);
        rent.setAmountPerMonth(new BigDecimal(perMonth));
        return rent;
    }

    private static EmployeeInvHomeLoan loan(String principal, String interest) {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan();
        loan.setId(UUID.randomUUID());
        loan.setTenantId(TENANT);
        loan.setDeclarationId(DECLARATION);
        loan.setPrincipalPaid(new BigDecimal(principal));
        loan.setInterestPaid(new BigDecimal(interest));
        loan.setFirstTimeBuyer(true);
        return loan;
    }

    @Test
    @DisplayName("No proof, or a proof not yet approved: every declared figure is used as it is")
    void notApprovedUsesDeclared() {
        when(reader.approvedAmounts(TENANT, DECLARATION)).thenReturn(Map.of());
        EmployeeInvHouseRent rent = rent("20000");
        EmployeeInvHomeLoan loan = loan("150000", "200000");

        ProofAdjustment.Approved approved = adjustment.approved(TENANT, declaration);

        assertThat(approved.isEmpty()).isTrue();
        assertThat(approved.houseRent(List.of(rent))).containsExactly(rent);
        assertThat(approved.homeLoans(List.of(loan))).containsExactly(loan);
        assertThat(approved.amount(ProofSourceKind.SECTION_6A, UUID.randomUUID(), new BigDecimal("50000")))
                .isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("APPROVED: approved figures per kind; house rent per month as the reader spread it")
    void approvedUsesApprovedPerKind() {
        EmployeeInvHouseRent rent = rent("20000");
        EmployeeInvHomeLoan loan = loan("150000", "200000");
        UUID section6a = UUID.randomUUID();
        UUID prevEmployment = UUID.randomUUID();
        UUID undeclaredInProof = UUID.randomUUID();
        when(reader.approvedAmounts(TENANT, DECLARATION))
                .thenReturn(Map.of(
                        Pair.of(ProofSourceKind.HOUSE_RENT, rent.getId()), new BigDecimal("15000.0000"),
                        Pair.of(ProofSourceKind.HOME_LOAN_PRINCIPAL, loan.getId()), new BigDecimal("100000.0000"),
                        Pair.of(ProofSourceKind.HOME_LOAN_INTEREST, loan.getId()), BigDecimal.ZERO,
                        Pair.of(ProofSourceKind.SECTION_6A, section6a), new BigDecimal("40000.0000"),
                        Pair.of(ProofSourceKind.PREV_EMPLOYMENT, prevEmployment), new BigDecimal("1000.0000")));

        ProofAdjustment.Approved approved = adjustment.approved(TENANT, declaration);

        EmployeeInvHouseRent rentUsed = approved.houseRent(List.of(rent)).get(0);
        assertThat(rentUsed.getAmountPerMonth()).isEqualByComparingTo("15000");
        assertThat(rentUsed.isMetro()).isTrue();
        assertThat(rentUsed.getFromMonth()).isEqualTo(rent.getFromMonth());
        EmployeeInvHomeLoan loanUsed = approved.homeLoans(List.of(loan)).get(0);
        assertThat(loanUsed.getPrincipalPaid()).isEqualByComparingTo("100000");
        assertThat(loanUsed.getInterestPaid())
                .as("a disallowed item counts as nothing")
                .isEqualByComparingTo("0");
        assertThat(loanUsed.isFirstTimeBuyer()).isTrue();
        assertThat(approved.amount(ProofSourceKind.SECTION_6A, section6a, new BigDecimal("50000")))
                .isEqualByComparingTo("40000");
        assertThat(approved.amount(ProofSourceKind.PREV_EMPLOYMENT, prevEmployment, new BigDecimal("2000")))
                .isEqualByComparingTo("1000");
        assertThat(approved.amount(ProofSourceKind.SECTION_6A, undeclaredInProof, new BigDecimal("7000")))
                .as("a line the proof has no item for keeps its declared figure")
                .isEqualByComparingTo("7000");

        // The declaration's managed rows are untouched: the adjusted ones are copies.
        assertThat(rentUsed).isNotSameAs(rent);
        assertThat(rent.getAmountPerMonth()).isEqualByComparingTo("20000");
        assertThat(loan.getPrincipalPaid()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("APPROVED let-out lines: the property's net is worked out again from the approved lines")
    void approvedLetOutRecomputesNet() {
        EmployeeInvLetOutProperty property = new EmployeeInvLetOutProperty();
        property.setId(UUID.randomUUID());
        property.setTenantId(TENANT);
        property.setDeclarationId(DECLARATION);
        property.setNetIncomeLoss(new BigDecimal("-100000"));
        EmployeeInvLetOutPropertyLine rentLine = line(property, LetOutPropertyLineType.ANNUAL_RENT, "300000");
        EmployeeInvLetOutPropertyLine interestLine = line(property, LetOutPropertyLineType.LOAN_INTEREST, "310000");
        when(lineRepository.findByTenantIdAndDeclarationId(TENANT, DECLARATION))
                .thenReturn(List.of(rentLine, interestLine));
        when(ruleReader.getStandardDeductionPercent("2026-2027", declaration.getTaxRegime()))
                .thenReturn(new BigDecimal("30.00"));
        when(reader.approvedAmounts(TENANT, DECLARATION))
                .thenReturn(Map.of(
                        Pair.of(ProofSourceKind.LET_OUT_PROPERTY, interestLine.getId()), new BigDecimal("200000")));

        EmployeeInvLetOutProperty used = adjustment
                .approved(TENANT, declaration)
                .letOutProperties(List.of(property))
                .get(0);

        // 300000 rent, 30% standard deduction = 210000, less the approved 200000 interest.
        assertThat(used.getNetIncomeLoss()).isEqualByComparingTo("10000");
        assertThat(property.getNetIncomeLoss()).isEqualByComparingTo("-100000");
    }

    private static EmployeeInvLetOutPropertyLine line(
            EmployeeInvLetOutProperty property, LetOutPropertyLineType type, String amount) {
        EmployeeInvLetOutPropertyLine line = new EmployeeInvLetOutPropertyLine();
        line.setId(UUID.randomUUID());
        line.setTenantId(TENANT);
        line.setDeclarationId(DECLARATION);
        line.setPropertyId(property.getId());
        line.setLineType(type);
        line.setAmount(new BigDecimal(amount));
        return line;
    }
}
