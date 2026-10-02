package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.proof.Pair;
import com.infinevo.payroll.proof.ProofAmountReader;
import com.infinevo.payroll.proof.ProofSourceKind;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLine;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyLineRepository;
import com.infinevo.payroll.taxdeclaration.housing.HousingRules;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyLineType;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyRuleReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Puts verified proof figures in place of declared ones for the tax calculation (W-34.2 spec sections 2 and 4).
 *
 * <p>{@link ProofAmountReader} is empty unless the declaration's proof is {@code APPROVED}, so with no proof or
 * a proof still under review every method hands the declared figures back unchanged. A declared line the proof
 * has no item for (an amount of zero when the proof was raised) keeps its declared figure; an item the reviewer
 * disallowed counts as zero.
 *
 * <p>Rows are never changed in place: the declaration rows are managed entities, and a changed amount would be
 * flushed back into the employee's declaration. Every adjusted row is a detached copy.
 */
@Component
public class ProofAdjustment {

    private final ProofAmountReader proofAmountReader;
    private final EmployeeInvLetOutPropertyLineRepository letOutLineRepository;
    private final LetOutPropertyRuleReader letOutPropertyRuleReader;

    public ProofAdjustment(
            ProofAmountReader proofAmountReader,
            EmployeeInvLetOutPropertyLineRepository letOutLineRepository,
            LetOutPropertyRuleReader letOutPropertyRuleReader) {
        this.proofAmountReader = Objects.requireNonNull(proofAmountReader, "proofAmountReader must not be null");
        this.letOutLineRepository =
                Objects.requireNonNull(letOutLineRepository, "letOutLineRepository must not be null");
        this.letOutPropertyRuleReader =
                Objects.requireNonNull(letOutPropertyRuleReader, "letOutPropertyRuleReader must not be null");
    }

    /** The approved figures for one declaration, read once per tax computation. Empty means "use declared". */
    public Approved approved(UUID tenantId, EmployeeInvestmentDeclaration declaration) {
        return new Approved(tenantId, declaration, proofAmountReader.approvedAmounts(tenantId, declaration.getId()));
    }

    /** One declaration's approved figures, and the adjusted copies of its rows. */
    public final class Approved {

        private final UUID tenantId;
        private final EmployeeInvestmentDeclaration declaration;
        private final Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts;

        private Approved(
                UUID tenantId,
                EmployeeInvestmentDeclaration declaration,
                Map<Pair<ProofSourceKind, UUID>, BigDecimal> amounts) {
            this.tenantId = tenantId;
            this.declaration = declaration;
            this.amounts = amounts == null ? Map.of() : amounts;
        }

        public boolean isEmpty() {
            return amounts.isEmpty();
        }

        /** Whether the approved proof has a figure for this declared line. */
        public boolean has(ProofSourceKind kind, UUID lineId) {
            return lineId != null && amounts.containsKey(Pair.of(kind, lineId));
        }

        /** The approved figure for one declared line, or the declared figure when the proof has none. */
        public BigDecimal amount(ProofSourceKind kind, UUID lineId, BigDecimal declared) {
            if (lineId == null) {
                return declared;
            }
            BigDecimal approved = amounts.get(Pair.of(kind, lineId));
            return approved != null ? approved : declared;
        }

        /** House rent: the reader already spreads the approved total over the line's months. */
        public List<EmployeeInvHouseRent> houseRent(List<EmployeeInvHouseRent> rows) {
            if (isEmpty() || rows == null) {
                return rows;
            }
            List<EmployeeInvHouseRent> out = new ArrayList<>(rows.size());
            for (EmployeeInvHouseRent row : rows) {
                if (!has(ProofSourceKind.HOUSE_RENT, row.getId())) {
                    out.add(row);
                    continue;
                }
                EmployeeInvHouseRent copy = new EmployeeInvHouseRent();
                copy.setId(row.getId());
                copy.setTenantId(row.getTenantId());
                copy.setDeclarationId(row.getDeclarationId());
                copy.setFromMonth(row.getFromMonth());
                copy.setToMonth(row.getToMonth());
                copy.setAddress(row.getAddress());
                copy.setLandlordName(row.getLandlordName());
                copy.setLandlordPan(row.getLandlordPan());
                copy.setMetro(row.isMetro());
                copy.setAmountPerMonth(amount(ProofSourceKind.HOUSE_RENT, row.getId(), row.getAmountPerMonth()));
                out.add(copy);
            }
            return out;
        }

        /** Home loans: principal and interest are proved as two separate items on the same loan. */
        public List<EmployeeInvHomeLoan> homeLoans(List<EmployeeInvHomeLoan> rows) {
            if (isEmpty() || rows == null) {
                return rows;
            }
            List<EmployeeInvHomeLoan> out = new ArrayList<>(rows.size());
            for (EmployeeInvHomeLoan row : rows) {
                if (!has(ProofSourceKind.HOME_LOAN_PRINCIPAL, row.getId())
                        && !has(ProofSourceKind.HOME_LOAN_INTEREST, row.getId())) {
                    out.add(row);
                    continue;
                }
                EmployeeInvHomeLoan copy = new EmployeeInvHomeLoan();
                copy.setId(row.getId());
                copy.setTenantId(row.getTenantId());
                copy.setDeclarationId(row.getDeclarationId());
                copy.setLenderName(row.getLenderName());
                copy.setLenderPan(row.getLenderPan());
                copy.setFirstTimeBuyer(row.isFirstTimeBuyer());
                copy.setLoanSanctionedOn(row.getLoanSanctionedOn());
                copy.setPrincipalPaid(amount(ProofSourceKind.HOME_LOAN_PRINCIPAL, row.getId(), row.getPrincipalPaid()));
                copy.setInterestPaid(amount(ProofSourceKind.HOME_LOAN_INTEREST, row.getId(), row.getInterestPaid()));
                out.add(copy);
            }
            return out;
        }

        /**
         * Let-out property: the proof covers the lines (rent, municipal tax, loan interest), and the engine reads
         * the property's net figure, so a property with any proved line has its net worked out again from the
         * approved lines with the same rule the declaration screen used.
         */
        public List<EmployeeInvLetOutProperty> letOutProperties(List<EmployeeInvLetOutProperty> rows) {
            if (isEmpty() || rows == null || rows.isEmpty()) {
                return rows;
            }
            List<EmployeeInvLetOutPropertyLine> lines =
                    letOutLineRepository.findByTenantIdAndDeclarationId(tenantId, declaration.getId());
            BigDecimal stdDedPct = null;
            List<EmployeeInvLetOutProperty> out = new ArrayList<>(rows.size());
            for (EmployeeInvLetOutProperty row : rows) {
                BigDecimal rent = BigDecimal.ZERO;
                BigDecimal municipalTax = BigDecimal.ZERO;
                BigDecimal loanInterest = BigDecimal.ZERO;
                boolean proved = false;
                for (EmployeeInvLetOutPropertyLine line : lines) {
                    if (!row.getId().equals(line.getPropertyId()) || line.getAmount() == null) {
                        continue;
                    }
                    BigDecimal figure = amount(ProofSourceKind.LET_OUT_PROPERTY, line.getId(), line.getAmount());
                    proved |= has(ProofSourceKind.LET_OUT_PROPERTY, line.getId());
                    if (line.getLineType() == LetOutPropertyLineType.ANNUAL_RENT) {
                        rent = figure;
                    } else if (line.getLineType() == LetOutPropertyLineType.MUNICIPAL_TAX) {
                        municipalTax = figure;
                    } else if (line.getLineType() == LetOutPropertyLineType.LOAN_INTEREST) {
                        loanInterest = figure;
                    }
                }
                if (!proved) {
                    out.add(row);
                    continue;
                }
                if (stdDedPct == null) {
                    stdDedPct = letOutPropertyRuleReader.getStandardDeductionPercent(
                            declaration.getFinancialYear(), declaration.getTaxRegime());
                }
                EmployeeInvLetOutProperty copy = new EmployeeInvLetOutProperty();
                copy.setId(row.getId());
                copy.setTenantId(row.getTenantId());
                copy.setDeclarationId(row.getDeclarationId());
                copy.setPropertyName(row.getPropertyName());
                copy.setAddress(row.getAddress());
                copy.setNetIncomeLoss(HousingRules.calculateNetIncomeLoss(rent, municipalTax, loanInterest, stdDedPct));
                out.add(copy);
            }
            return out;
        }
    }
}
