package com.infinevo.core.tenant.service.impl;

import com.infinevo.core.tenant.dto.TenantEntitlementResponse;
import com.infinevo.core.tenant.entity.SubscriptionModule;
import com.infinevo.core.tenant.repository.SubscriptionModuleRepository;
import com.infinevo.core.tenant.service.EntitlementService;
import com.infinevo.shared.entitlement.ModuleCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementServiceImpl implements EntitlementService {

    private final SubscriptionModuleRepository subscriptionModuleRepository;

    public EntitlementServiceImpl(SubscriptionModuleRepository subscriptionModuleRepository) {
        this.subscriptionModuleRepository = subscriptionModuleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTenantEntitled(UUID tenantId, ModuleCode moduleCode) {
        if (tenantId == null) {
            return false;
        }
        if (moduleCode == ModuleCode.CORE) {
            return true;
        }
        return subscriptionModuleRepository.existsByTenantIdAndModuleCodeAndStatus(tenantId, moduleCode, "ACTIVE");
    }

    @Override
    @Transactional(readOnly = true)
    public TenantEntitlementResponse getTenantEntitlements(UUID tenantId) {
        List<SubscriptionModule> activeModules =
                subscriptionModuleRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        List<ModuleCode> modules = new ArrayList<>();
        modules.add(ModuleCode.CORE);

        for (SubscriptionModule mod : activeModules) {
            if (!modules.contains(mod.getModuleCode())) {
                modules.add(mod.getModuleCode());
            }
        }

        List<TenantEntitlementResponse.NavItem> navigation = buildNavigationFeed(modules);
        return new TenantEntitlementResponse(tenantId, modules, navigation);
    }

    private List<TenantEntitlementResponse.NavItem> buildNavigationFeed(List<ModuleCode> modules) {
        List<TenantEntitlementResponse.NavItem> navItems = new ArrayList<>();
        navItems.add(new TenantEntitlementResponse.NavItem("dashboard", "Dashboard", "/dashboard", ModuleCode.CORE));

        if (modules.contains(ModuleCode.HRMS)) {
            navItems.add(new TenantEntitlementResponse.NavItem(
                    "hrms-employees", "Employees", "/hrms/employees", ModuleCode.HRMS));
            navItems.add(new TenantEntitlementResponse.NavItem(
                    "hrms-attendance", "Attendance", "/hrms/attendance", ModuleCode.HRMS));
        }

        if (modules.contains(ModuleCode.PAYROLL)) {
            navItems.add(new TenantEntitlementResponse.NavItem(
                    "payroll-runs", "Payroll Runs", "/payroll/runs", ModuleCode.PAYROLL));
            navItems.add(new TenantEntitlementResponse.NavItem(
                    "payroll-slips", "Payslips", "/payroll/slips", ModuleCode.PAYROLL));
        }

        return navItems;
    }
}
