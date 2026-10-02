package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6A;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.EnteredBy;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLine;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLineRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads a declaration's six kinds of line and reports the ones that need proof (W-34.1 spec section 3).
 *
 * <p>Pre-tax deductions and other income are not read: the first comes from payroll's own figures, the
 * second is income, not a saving (spec section 13, decision 2). Previous-employment lines an officer
 * entered are not read either: the employee cannot prove what an officer keyed in.
 *
 * <p>Every read is tenant-bound.
 */
@Component
public class ProofSourceReader {

    private final EmployeeInvHouseRentRepository houseRentRepository;
    private final EmployeeInvHomeLoanRepository homeLoanRepository;
    private final EmployeeInvLetOutPropertyLineRepository letOutLineRepository;
    private final EmployeeInvSection6ARepository section6aRepository;
    private final EmployeeInvPrevEmploymentRepository prevEmploymentRepository;

    public ProofSourceReader(
            EmployeeInvHouseRentRepository houseRentRepository,
            EmployeeInvHomeLoanRepository homeLoanRepository,
            EmployeeInvLetOutPropertyLineRepository letOutLineRepository,
            EmployeeInvSection6ARepository section6aRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository) {
        this.houseRentRepository = Objects.requireNonNull(houseRentRepository, "houseRentRepository");
        this.homeLoanRepository = Objects.requireNonNull(homeLoanRepository, "homeLoanRepository");
        this.letOutLineRepository = Objects.requireNonNull(letOutLineRepository, "letOutLineRepository");
        this.section6aRepository = Objects.requireNonNull(section6aRepository, "section6aRepository");
        this.prevEmploymentRepository = Objects.requireNonNull(prevEmploymentRepository, "prevEmploymentRepository");
    }

    /** Every line of the declaration that needs proof, amounts greater than zero only. */
    public List<ProofSourceLine> read(UUID tenantId, UUID declarationId) {
        List<ProofSourceLine> lines = new ArrayList<>();

        for (EmployeeInvHouseRent rent :
                houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId)) {
            BigDecimal declared = rent.getAmountPerMonth().multiply(BigDecimal.valueOf(months(rent)));
            add(
                    lines,
                    ProofSourceKind.HOUSE_RENT,
                    rent.getId(),
                    "House rent paid to " + rent.getLandlordName(),
                    declared);
        }
        for (EmployeeInvHomeLoan loan : homeLoanRepository.findByTenantIdAndDeclarationId(tenantId, declarationId)) {
            add(
                    lines,
                    ProofSourceKind.HOME_LOAN_PRINCIPAL,
                    loan.getId(),
                    "Home loan principal, " + loan.getLenderName(),
                    loan.getPrincipalPaid());
            add(
                    lines,
                    ProofSourceKind.HOME_LOAN_INTEREST,
                    loan.getId(),
                    "Home loan interest, " + loan.getLenderName(),
                    loan.getInterestPaid());
        }
        for (EmployeeInvLetOutPropertyLine line :
                letOutLineRepository.findByTenantIdAndDeclarationId(tenantId, declarationId)) {
            add(
                    lines,
                    ProofSourceKind.LET_OUT_PROPERTY,
                    line.getId(),
                    "Let-out property, " + line.getLineType(),
                    line.getAmount());
        }
        for (EmployeeInvSection6A section :
                section6aRepository.findByTenantIdAndDeclarationId(tenantId, declarationId)) {
            add(lines, ProofSourceKind.SECTION_6A, section.getId(), section.getDescription(), section.getAmount());
        }
        for (EmployeeInvPrevEmployment prev :
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declarationId)) {
            if (prev.getEnteredBy() != EnteredBy.EMPLOYEE) {
                continue;
            }
            add(
                    lines,
                    ProofSourceKind.PREV_EMPLOYMENT,
                    prev.getId(),
                    "Previous employment, " + prev.getKind(),
                    prev.getAmount());
        }
        return lines;
    }

    /** Months from {@code from_month} to {@code to_month}, both included: April to March is 12. */
    static long months(EmployeeInvHouseRent rent) {
        return months(rent.getFromMonth(), rent.getToMonth());
    }

    static long months(LocalDate from, LocalDate to) {
        long between = ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1;
        return Math.max(between, 0);
    }

    private static void add(
            List<ProofSourceLine> lines, ProofSourceKind kind, UUID id, String description, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            lines.add(new ProofSourceLine(kind, id, description, amount));
        }
    }
}
