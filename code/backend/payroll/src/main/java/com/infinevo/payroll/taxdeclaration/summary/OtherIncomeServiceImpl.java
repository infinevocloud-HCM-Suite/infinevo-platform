package com.infinevo.payroll.taxdeclaration.summary;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeResponse;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link OtherIncomeService} (W-32.4).
 */
@Service
@Transactional
public class OtherIncomeServiceImpl implements OtherIncomeService {

    private final TaxDeclarationService taxDeclarationService;
    private final EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private final TaxDeclarationWindowService windowService;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public OtherIncomeServiceImpl(
            TaxDeclarationService taxDeclarationService,
            EmployeeInvOtherIncomeRepository otherIncomeRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService) {
        this(
                taxDeclarationService,
                otherIncomeRepository,
                windowService,
                employeeService,
                TaxDeclarationRules.defaultClock());
    }

    OtherIncomeServiceImpl(
            TaxDeclarationService taxDeclarationService,
            EmployeeInvOtherIncomeRepository otherIncomeRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.otherIncomeRepository =
                Objects.requireNonNull(otherIncomeRepository, "otherIncomeRepository must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional
    public List<OtherIncomeResponse> readOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return read(current.id(), financialYear);
    }

    @Override
    @Transactional
    public List<OtherIncomeResponse> read(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        return readForDeclaration(tenantId, decl.getId());
    }

    @Override
    public List<OtherIncomeResponse> replaceOwn(String financialYear, List<OtherIncomeRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replaceInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public List<OtherIncomeResponse> replace(UUID employeeId, String financialYear, List<OtherIncomeRequest> requests) {
        return replaceInternal(employeeId, financialYear, requests, true);
    }

    private List<OtherIncomeResponse> replaceInternal(
            UUID employeeId, String financialYear, List<OtherIncomeRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);
        OtherIncomeRules.validate(requests);

        otherIncomeRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        otherIncomeRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvOtherIncome> entities = requests.stream()
                    .map(req -> new EmployeeInvOtherIncome(
                            tenantId,
                            decl.getId(),
                            req.kind(),
                            req.kind() == OtherIncomeKind.OTHER && req.description() != null
                                    ? req.description().trim()
                                    : null,
                            req.amount()))
                    .toList();
            otherIncomeRepository.saveAll(entities);
        }

        return readForDeclaration(tenantId, decl.getId());
    }

    private void validateEditable(EmployeeInvestmentDeclaration decl, boolean ignoreWindow) {
        IncomeTaxDeclarationWindow window =
                windowService.findOrCreateDefault(decl.getTenantId(), decl.getFinancialYear());
        LocalDate today = TaxDeclarationRules.today(clock);
        if (!TaxDeclarationRules.isEditable(decl, window, today, ignoreWindow)) {
            throw new DeclarationNotEditableException(
                    "NOT_EDITABLE", TaxDeclarationRules.notEditableReason(decl, window, today, ignoreWindow));
        }
    }

    private List<OtherIncomeResponse> readForDeclaration(UUID tenantId, UUID declarationId) {
        List<EmployeeInvOtherIncome> list =
                otherIncomeRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        return list.stream()
                .map(e -> new OtherIncomeResponse(e.getId(), e.getKind(), e.getDescription(), e.getAmount()))
                .toList();
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }
}
