package com.infinevo.core.org;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model service for organizational chart subtrees (W-14.2, spec section 4).
 */
@Service
public class OrgChartService {

    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_DEPTH = 10;

    private final ReportingLineRepository reportingLineRepository;
    private final EmployeeRepository employeeRepository;

    public OrgChartService(ReportingLineRepository reportingLineRepository, EmployeeRepository employeeRepository) {
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    /**
     * Builds the org chart subtree starting from rootEmployeeId up to the specified depth.
     */
    @Transactional(readOnly = true)
    public OrgChartNodeResponse getOrgChart(UUID rootEmployeeId, Integer depth) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(rootEmployeeId, "rootEmployeeId must not be null");

        Employee root = employeeRepository
                .findById(rootEmployeeId)
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isDeleted())
                .orElseThrow(
                        () -> new IllegalArgumentException("Root employee not found in tenant: " + rootEmployeeId));

        int targetDepth = depth != null ? Math.min(Math.max(depth, 1), MAX_DEPTH) : DEFAULT_DEPTH;
        LocalDate today = LocalDate.now();

        return buildTree(tenantId, root, targetDepth, 1, today);
    }

    private OrgChartNodeResponse buildTree(
            UUID tenantId, Employee current, int maxDepth, int currentDepth, LocalDate asOf) {
        List<OrgChartNodeResponse> children = new ArrayList<>();

        if (currentDepth < maxDepth) {
            List<ReportingLine> directReports =
                    reportingLineRepository.findDirectReports(tenantId, current.getId(), asOf);
            for (ReportingLine line : directReports) {
                Employee report = line.getEmployee();
                if (!report.isDeleted()) {
                    children.add(buildTree(tenantId, report, maxDepth, currentDepth + 1, asOf));
                }
            }
        }

        return OrgChartNodeResponse.from(current, children);
    }
}
