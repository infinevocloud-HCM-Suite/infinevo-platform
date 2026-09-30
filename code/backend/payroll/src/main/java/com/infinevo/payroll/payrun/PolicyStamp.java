package com.infinevo.payroll.payrun;

import com.infinevo.core.lop.LopPolicy;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasis;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * How one pay figure was reached (W-18.2): the loss-of-pay policy version, its working-day basis, the
 * divisor and payable days W-18.1's calculator returned, and the rounding rule. Every part is required —
 * there is no partial stamp and no default one, so a figure without a resolvable policy cannot be
 * written (W-18.2 §9).
 */
public record PolicyStamp(
        UUID policyId,
        WorkingDayBasis workingDayBasis,
        BigDecimal divisor,
        BigDecimal payableDays,
        LopRounding lopRounding) {

    public PolicyStamp {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(workingDayBasis, "workingDayBasis must not be null");
        Objects.requireNonNull(divisor, "divisor must not be null");
        Objects.requireNonNull(payableDays, "payableDays must not be null");
        Objects.requireNonNull(lopRounding, "lopRounding must not be null");
    }

    /**
     * The stamp for a figure computed from {@code basis}, with the basis named by {@code policy} — the
     * version the calculator used.
     *
     * @throws IllegalStateException if {@code policy} is not the version the calculator stamped
     */
    public static PolicyStamp of(WorkingDayBasisResponse basis, LopPolicy policy) {
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (!policy.getId().equals(basis.policyId())) {
            throw new IllegalStateException("The working-day basis came from loss-of-pay policy " + basis.policyId()
                    + ", not the policy in force " + policy.getId() + "; the figure cannot be stamped");
        }
        return new PolicyStamp(
                basis.policyId(),
                policy.getWorkingDayBasis(),
                basis.divisor(),
                basis.payableDays(),
                basis.lopRounding());
    }
}
