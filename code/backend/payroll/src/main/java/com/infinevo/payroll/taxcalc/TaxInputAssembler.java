package com.infinevo.payroll.taxcalc;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeDetailService;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.taxcalc.engine.SalaryProjection;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Gathers and aggregates all input needed for tax calculation across employee, salary, and
 * declaration tables (W-33.1 spec § 3, § 4).
 */
@Component
public class TaxInputAssembler {

    private final TaxDeclarationService declarationService;
    private final EmployeeService employeeService;
    private final EmployeePersonalService employeePersonalService;
    private final EmployeeSalaryService employeeSalaryService;
    private final EarningRepository earningRepository;
    private final EmployeeInvPrevEmploymentRepository prevEmploymentRepository;

    public TaxInputAssembler(
            TaxDeclarationService declarationService,
            EmployeeService employeeService,
            EmployeePersonalService employeePersonalService,
            EmployeeSalaryService employeeSalaryService,
            EarningRepository earningRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository) {
        this.declarationService = Objects.requireNonNull(declarationService, "declarationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeePersonalService =
                Objects.requireNonNull(employeePersonalService, "employeePersonalService must not be null");
        this.employeeSalaryService =
                Objects.requireNonNull(employeeSalaryService, "employeeSalaryService must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.prevEmploymentRepository =
                Objects.requireNonNull(prevEmploymentRepository, "prevEmploymentRepository must not be null");
    }

    /**
     * Gathers inputs for the given employee and financial year in the given tenant.
     *
     * @param tenantId tenant identifier
     * @param employeeId employee identifier
     * @param fy the financial year
     * @return assembled {@link TaxInput}
     */
    public TaxInput assemble(UUID tenantId, UUID employeeId, FinancialYear fy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(fy, "fy must not be null");

        EmployeeInvestmentDeclaration declaration = declarationService.require(employeeId, fy.label());
        EmployeeResponse employee = employeeService.get(employeeId);

        LocalDate dateOfJoining = employee.dateOfJoining();
        LocalDate dateOfBirth = null;
        try {
            EmployeePersonalResponse personal = employeePersonalService.get(employeeId);
            if (personal != null) {
                dateOfBirth = personal.dateOfBirth();
            }
        } catch (EmployeeDetailService.NotFoundException e) {
            // General category when personal section not written
        }

        AgeCategory ageCategory = AgeCategory.at(dateOfBirth, fy.end());

        SalaryProjectionResult salaryResult = SalaryProjection.annual(
                fy,
                dateOfJoining,
                date -> employeeSalaryService.versionInForce(tenantId, employeeId, date),
                componentId -> earningRepository
                        .findByIdAndTenantIdAndDeletedFalse(componentId, tenantId)
                        .map(Earning::isTaxable)
                        .orElse(false));

        List<EmployeeInvPrevEmployment> prevEmploymentRows =
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declaration.getId());

        Map<PrevEmploymentKind, Money> prevEmploymentMap = new EnumMap<>(PrevEmploymentKind.class);
        for (EmployeeInvPrevEmployment row : prevEmploymentRows) {
            Money current = prevEmploymentMap.getOrDefault(row.getKind(), Money.ZERO);
            prevEmploymentMap.put(row.getKind(), current.add(Money.of(row.getAmount())));
        }

        return new TaxInput(employeeId, declaration.getId(), salaryResult, prevEmploymentMap, ageCategory);
    }
}
