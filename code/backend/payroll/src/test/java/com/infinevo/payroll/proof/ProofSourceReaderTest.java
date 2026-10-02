package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6A;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.EnteredBy;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLine;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLineRepository;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyLineType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-34.1 — the six kinds of declared line and their declared amounts (spec section 3 table). */
class ProofSourceReaderTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DECLARATION = UUID.randomUUID();

    private EmployeeInvHouseRentRepository rents;
    private EmployeeInvHomeLoanRepository loans;
    private EmployeeInvLetOutPropertyLineRepository letOut;
    private EmployeeInvSection6ARepository section6a;
    private EmployeeInvPrevEmploymentRepository prev;
    private ProofSourceReader reader;

    @BeforeEach
    void setUp() {
        rents = mock(EmployeeInvHouseRentRepository.class);
        loans = mock(EmployeeInvHomeLoanRepository.class);
        letOut = mock(EmployeeInvLetOutPropertyLineRepository.class);
        section6a = mock(EmployeeInvSection6ARepository.class);
        prev = mock(EmployeeInvPrevEmploymentRepository.class);
        reader = new ProofSourceReader(rents, loans, letOut, section6a, prev);
    }

    @Test
    @DisplayName("months are counted inclusively: April to March is 12, one month is 1")
    void monthsInclusive() {
        assertThat(ProofSourceReader.months(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 1)))
                .isEqualTo(12);
        assertThat(ProofSourceReader.months(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 1)))
                .isEqualTo(1);
        assertThat(ProofSourceReader.months(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28)))
                .as("the day of the month does not matter")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("house rent is the monthly rent times the months of the range")
    void houseRent() {
        EmployeeInvHouseRent rent = mock(EmployeeInvHouseRent.class);
        UUID id = UUID.randomUUID();
        when(rent.getId()).thenReturn(id);
        when(rent.getFromMonth()).thenReturn(LocalDate.of(2025, 4, 1));
        when(rent.getToMonth()).thenReturn(LocalDate.of(2026, 3, 1));
        when(rent.getAmountPerMonth()).thenReturn(new BigDecimal("15000.0000"));
        when(rent.getLandlordName()).thenReturn("A. Landlord");
        when(rents.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(TENANT, DECLARATION))
                .thenReturn(List.of(rent));

        List<ProofSourceLine> lines = reader.read(TENANT, DECLARATION);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).kind()).isEqualTo(ProofSourceKind.HOUSE_RENT);
        assertThat(lines.get(0).lineId()).isEqualTo(id);
        assertThat(lines.get(0).declaredAmount()).isEqualByComparingTo("180000");
    }

    @Test
    @DisplayName("a home loan gives a principal line and an interest line, each only when above zero")
    void homeLoan() {
        EmployeeInvHomeLoan both = loan("Bank A", "50000", "120000");
        EmployeeInvHomeLoan interestOnly = loan("Bank B", "0", "30000");
        when(loans.findByTenantIdAndDeclarationId(TENANT, DECLARATION)).thenReturn(List.of(both, interestOnly));

        List<ProofSourceLine> lines = reader.read(TENANT, DECLARATION);

        assertThat(lines)
                .extracting(ProofSourceLine::kind)
                .containsExactly(
                        ProofSourceKind.HOME_LOAN_PRINCIPAL,
                        ProofSourceKind.HOME_LOAN_INTEREST,
                        ProofSourceKind.HOME_LOAN_INTEREST);
        assertThat(lines).extracting(l -> l.declaredAmount().intValue()).containsExactly(50000, 120000, 30000);
    }

    @Test
    @DisplayName("let-out property and Section 6A lines carry their own amount; zero amounts are skipped")
    void letOutAndSection6a() {
        EmployeeInvLetOutPropertyLine interest = mock(EmployeeInvLetOutPropertyLine.class);
        when(interest.getId()).thenReturn(UUID.randomUUID());
        when(interest.getLineType()).thenReturn(LetOutPropertyLineType.values()[0]);
        when(interest.getAmount()).thenReturn(new BigDecimal("8000"));
        EmployeeInvLetOutPropertyLine zero = mock(EmployeeInvLetOutPropertyLine.class);
        when(zero.getId()).thenReturn(UUID.randomUUID());
        when(zero.getLineType()).thenReturn(LetOutPropertyLineType.values()[0]);
        when(zero.getAmount()).thenReturn(BigDecimal.ZERO);
        when(letOut.findByTenantIdAndDeclarationId(TENANT, DECLARATION)).thenReturn(List.of(interest, zero));

        EmployeeInvSection6A eighty = mock(EmployeeInvSection6A.class);
        when(eighty.getId()).thenReturn(UUID.randomUUID());
        when(eighty.getDescription()).thenReturn("Section 80C");
        when(eighty.getAmount()).thenReturn(new BigDecimal("150000"));
        when(section6a.findByTenantIdAndDeclarationId(TENANT, DECLARATION)).thenReturn(List.of(eighty));

        List<ProofSourceLine> lines = reader.read(TENANT, DECLARATION);

        assertThat(lines)
                .extracting(ProofSourceLine::kind)
                .containsExactly(ProofSourceKind.LET_OUT_PROPERTY, ProofSourceKind.SECTION_6A);
        assertThat(lines.get(1).description()).isEqualTo("Section 80C");
    }

    @Test
    @DisplayName("previous employment counts only when the employee entered it, not an officer")
    void previousEmploymentOnlyWhenEnteredByEmployee() {
        EmployeeInvPrevEmployment byEmployee = prevEmployment(EnteredBy.EMPLOYEE, "40000");
        EmployeeInvPrevEmployment byOfficer = prevEmployment(EnteredBy.values()[1], "90000");
        when(prev.findByTenantIdAndDeclarationId(TENANT, DECLARATION)).thenReturn(List.of(byEmployee, byOfficer));

        List<ProofSourceLine> lines = reader.read(TENANT, DECLARATION);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).kind()).isEqualTo(ProofSourceKind.PREV_EMPLOYMENT);
        assertThat(lines.get(0).declaredAmount()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("a declaration with nothing declared has nothing to prove")
    void nothingDeclared() {
        assertThat(reader.read(TENANT, DECLARATION)).isEmpty();
    }

    private static EmployeeInvHomeLoan loan(String lender, String principal, String interest) {
        EmployeeInvHomeLoan loan = mock(EmployeeInvHomeLoan.class);
        when(loan.getId()).thenReturn(UUID.randomUUID());
        when(loan.getLenderName()).thenReturn(lender);
        when(loan.getPrincipalPaid()).thenReturn(new BigDecimal(principal));
        when(loan.getInterestPaid()).thenReturn(new BigDecimal(interest));
        return loan;
    }

    private static EmployeeInvPrevEmployment prevEmployment(EnteredBy by, String amount) {
        EmployeeInvPrevEmployment p = mock(EmployeeInvPrevEmployment.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        when(p.getEnteredBy()).thenReturn(by);
        when(p.getKind()).thenReturn(PrevEmploymentKind.values()[0]);
        when(p.getAmount()).thenReturn(new BigDecimal(amount));
        return p;
    }
}
