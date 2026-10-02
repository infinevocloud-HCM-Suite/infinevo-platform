package com.infinevo.payroll.proof;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Reads approved amounts from an employee's verified proof of investment (W-34.2).
 *
 * <p>Used by tax calculation ({@code TaxInputGatherer}) to substitute verified figures
 * for declared ones when the proof is {@link ProofStatus#APPROVED}.
 */
public interface ProofAmountReader {

    /**
     * Returns approved amounts for the given declaration using the current tenant from {@link com.infinevo.shared.tenant.TenantContext}.
     *
     * @param declarationId declaration ID
     * @return map keyed by {@code (sourceKind, sourceLineId)}; empty if proof does not exist or is not {@code APPROVED}
     */
    Map<Pair<ProofSourceKind, UUID>, BigDecimal> approvedAmounts(UUID declarationId);

    /**
     * Returns approved amounts for the given declaration under the specified tenant.
     *
     * @param tenantId tenant ID
     * @param declarationId declaration ID
     * @return map keyed by {@code (sourceKind, sourceLineId)}; empty if proof does not exist or is not {@code APPROVED}
     */
    Map<Pair<ProofSourceKind, UUID>, BigDecimal> approvedAmounts(UUID tenantId, UUID declarationId);
}
