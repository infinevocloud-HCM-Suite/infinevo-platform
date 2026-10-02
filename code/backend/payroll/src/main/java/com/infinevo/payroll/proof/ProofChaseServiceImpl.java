package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ProofChaseService} (W-34.3).
 */
@Service
@Transactional(readOnly = true)
public class ProofChaseServiceImpl implements ProofChaseService {

    private final ProofPendingQuery proofPendingQuery;

    public ProofChaseServiceImpl(ProofPendingQuery proofPendingQuery) {
        this.proofPendingQuery = Objects.requireNonNull(proofPendingQuery, "proofPendingQuery must not be null");
    }

    @Override
    public Page<ProofChaseRow> list(String financialYear, String status, String search, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        FinancialYear fy = FinancialYear.parse(financialYear);
        return proofPendingQuery.findChaseRows(tenantId, fy.label(), status, search, pageable);
    }

    @Override
    public ProofChaseSummary summary(String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear fy = FinancialYear.parse(financialYear);
        return proofPendingQuery.getSummary(tenantId, fy.label());
    }
}
