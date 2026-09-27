package com.infinevo.core.org;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        int targetDepth = (depth == null || depth <= 0) ? DEFAULT_DEPTH : Math.min(depth, MAX_DEPTH);
        LocalDate today = LocalDate.now();

        // Single query for all active primary reporting lines in the tenant (eliminates N+1 DB calls)
        List<ReportingLine> allPrimaryLines = reportingLineRepository.findAllActivePrimaryLines(tenantId, today);

        Map<UUID, List<Employee>> reportsByManager = new HashMap<>();
        for (ReportingLine line : allPrimaryLines) {
            Employee emp = line.getEmployee();
            Employee mgr = line.getManager();
            if (!emp.isDeleted() && !mgr.isDeleted()) {
                reportsByManager
                        .computeIfAbsent(mgr.getId(), k -> new ArrayList<>())
                        .add(emp);
            }
        }

        return buildInMemoryTree(root, reportsByManager, targetDepth, 1);
    }

    private OrgChartNodeResponse buildInMemoryTree(
            Employee current, Map<UUID, List<Employee>> reportsByManager, int maxDepth, int currentDepth) {
        List<OrgChartNodeResponse> children = new ArrayList<>();

        if (currentDepth < maxDepth) {
            List<Employee> directReports = reportsByManager.getOrDefault(current.getId(), List.of());
            for (Employee report : directReports) {
                children.add(buildInMemoryTree(report, reportsByManager, maxDepth, currentDepth + 1));
            }
        }

        return OrgChartNodeResponse.from(current, children);
    }
}
