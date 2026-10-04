package com.infinevo.payroll.navigation;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import com.infinevo.core.navigation.NavigationContributor;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Payroll's menu items (W-47.2 §5). Each is here because its endpoint is in this module: the boot-time
 * catalogue check refuses an item whose {@code targetEndpoint} has no {@code GET} mapping.
 */
@Component
public class PayrollNavigation implements NavigationContributor {

    /** The payroll dashboard — {@code PayrollDashboardController}'s {@code GET /api/v1/payroll/dashboard} (W-47.5 §4). Listed first. */
    public static final ItemDefinition DASHBOARD = new ItemDefinition(
            "payroll.dashboard",
            "nav.payroll.dashboard",
            "/payroll/dashboard",
            "/api/v1/payroll/dashboard",
            PlatformModule.PAYROLL,
            "payroll.run.read");

    /** The pay run list — {@code PayRunController}'s {@code GET /api/v1/payroll/payruns}. */
    public static final ItemDefinition RUNS = new ItemDefinition(
            "payroll.runs",
            "nav.payroll.runs",
            "/payroll/runs",
            "/api/v1/payroll/payruns",
            PlatformModule.PAYROLL,
            "payroll.run.read");

    /** Prior payroll import & history — {@code PriorPayrollController}'s {@code GET /api/v1/payroll/prior-payroll-imports} (W-38.1 §4). */
    public static final ItemDefinition PRIOR_PAYROLL = new ItemDefinition(
            "payroll.prior_payroll",
            "nav.payroll.prior_payroll",
            "/payroll/prior-payroll",
            "/api/v1/payroll/prior-payroll-imports",
            PlatformModule.PAYROLL,
            "payroll.run.read");

    /** Reimbursement claims — {@code ReimbursementClaimController}'s {@code GET /api/v1/payroll/reimbursement-claims} (W-47.4 §4). */
    public static final ItemDefinition CLAIMS = new ItemDefinition(
            "payroll.claims",
            "nav.payroll.claims",
            "/payroll/claims",
            "/api/v1/payroll/reimbursement-claims",
            PlatformModule.PAYROLL,
            "payroll.reimbursement_claim.read");

    /** Ad-hoc deductions — {@code EmployeeDeductionController}'s {@code GET /api/v1/payroll/employee-deductions} (W-47.4 §4). */
    public static final ItemDefinition DEDUCTIONS = new ItemDefinition(
            "payroll.deductions",
            "nav.payroll.deductions",
            "/payroll/deductions",
            "/api/v1/payroll/employee-deductions",
            PlatformModule.PAYROLL,
            "payroll.employee_deduction.read");

    @Override
    public List<ItemDefinition> items() {
        return List.of(DASHBOARD, RUNS, PRIOR_PAYROLL, CLAIMS, DEDUCTIONS);
    }
}
