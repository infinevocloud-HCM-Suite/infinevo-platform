package com.infinevo.payroll.statutory.ecr;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for PF ECR in-app preview and audit validation (W-87 §5).
 */
public record PfEcrPreviewResponse(
        UUID payrunId,
        String period,
        String establishmentCode,
        int totalMembers,
        long totalGrossWages,
        long totalEpfWages,
        long totalEpsWages,
        long totalEdliWages,
        long totalEeShare,
        long totalEpsShare,
        long totalErShare,
        long totalAdminCharges,
        long totalEdliCharges,
        long grandTotalChallan,
        List<PfEcrWarning> warnings,
        List<PfEcrRowResponse> rows) {

    public PfChallanAccountSummary accountSummary() {
        return new PfChallanAccountSummary(
                totalEeShare + totalErShare, totalAdminCharges, totalEpsShare, totalEdliCharges, 0L, grandTotalChallan);
    }
}
