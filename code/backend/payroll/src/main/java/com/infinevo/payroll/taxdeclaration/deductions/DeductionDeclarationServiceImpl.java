package com.infinevo.payroll.taxdeclaration.deductions;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader.Section6AItem;
import com.infinevo.payroll.taxdeclaration.deductions.dto.DeductionDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6AItemResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link DeductionDeclarationService} (W-32.3).
 */
@Service
@Transactional
public class DeductionDeclarationServiceImpl implements DeductionDeclarationService {

    private final TaxDeclarationService taxDeclarationService;
    private final EmployeeInvSection6ARepository section6ARepository;
    private final EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;
    private final EmployeeInvPrevEmploymentRepository prevEmploymentRepository;
    private final Section6AItemReader section6AItemReader;
    private final TaxDeclarationWindowService windowService;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public DeductionDeclarationServiceImpl(
            TaxDeclarationService taxDeclarationService,
            EmployeeInvSection6ARepository section6ARepository,
            EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository,
            Section6AItemReader section6AItemReader,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService) {
        this(
                taxDeclarationService,
                section6ARepository,
                preTaxDeductionRepository,
                prevEmploymentRepository,
                section6AItemReader,
                windowService,
                employeeService,
                TaxDeclarationRules.defaultClock());
    }

    DeductionDeclarationServiceImpl(
            TaxDeclarationService taxDeclarationService,
            EmployeeInvSection6ARepository section6ARepository,
            EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository,
            Section6AItemReader section6AItemReader,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.section6ARepository = Objects.requireNonNull(section6ARepository, "section6ARepository must not be null");
        this.preTaxDeductionRepository =
                Objects.requireNonNull(preTaxDeductionRepository, "preTaxDeductionRepository must not be null");
        this.prevEmploymentRepository =
                Objects.requireNonNull(prevEmploymentRepository, "prevEmploymentRepository must not be null");
        this.section6AItemReader = Objects.requireNonNull(section6AItemReader, "section6AItemReader must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Section6AItemResponse> getSection6AItemsOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return getSection6AItems(current.id(), financialYear);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Section6AItemResponse> getSection6AItems(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        List<Section6AItem> items = section6AItemReader.activeItems(decl.getTaxRegime());
        return items.stream()
                .map(item -> new Section6AItemResponse(
                        item.id(),
                        item.sectionCode(),
                        item.category(),
                        item.name(),
                        item.description(),
                        item.maxLimit(),
                        item.categoryGroupCode(),
                        item.is80c(),
                        item.is80d()))
                .toList();
    }

    @Override
    @Transactional
    public DeductionDeclarationResponse readOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return read(current.id(), financialYear);
    }

    @Override
    @Transactional
    public DeductionDeclarationResponse read(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        return readForDeclaration(tenantId, decl.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DeductionDeclarationResponse readByDeclarationId(UUID declarationId) {
        UUID tenantId = TenantContext.require();
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(declarationId);
        return readForDeclaration(tenantId, decl.getId());
    }

    @Override
    public DeductionDeclarationResponse replaceSection6AOwn(String financialYear, List<Section6ALineRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replaceSection6AInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public DeductionDeclarationResponse replaceSection6A(
            UUID employeeId, String financialYear, List<Section6ALineRequest> requests) {
        return replaceSection6AInternal(employeeId, financialYear, requests, true);
    }

    @Override
    public DeductionDeclarationResponse replacePreTaxDeductionsOwn(
            String financialYear, List<PreTaxDeductionRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replacePreTaxDeductionsInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public DeductionDeclarationResponse replacePreTaxDeductions(
            UUID employeeId, String financialYear, List<PreTaxDeductionRequest> requests) {
        return replacePreTaxDeductionsInternal(employeeId, financialYear, requests, true);
    }

    @Override
    public DeductionDeclarationResponse replacePrevEmploymentOwn(
            String financialYear, List<PrevEmploymentRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replacePrevEmploymentInternal(current.id(), financialYear, requests, false, EnteredBy.EMPLOYEE);
    }

    @Override
    public DeductionDeclarationResponse replacePrevEmployment(
            UUID employeeId, String financialYear, List<PrevEmploymentRequest> requests) {
        return replacePrevEmploymentInternal(employeeId, financialYear, requests, true, EnteredBy.OFFICER);
    }

    private DeductionDeclarationResponse replaceSection6AInternal(
            UUID employeeId, String financialYear, List<Section6ALineRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);
        DeductionRules.validateSection6A(requests, decl.getTaxRegime(), section6AItemReader);

        section6ARepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        section6ARepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvSection6A> entities = requests.stream()
                    .map(req -> new EmployeeInvSection6A(
                            tenantId,
                            decl.getId(),
                            req.section6aItemId(),
                            req.description().trim(),
                            req.amount()))
                    .toList();
            section6ARepository.saveAll(entities);
        }

        return readForDeclaration(tenantId, decl.getId());
    }

    private DeductionDeclarationResponse replacePreTaxDeductionsInternal(
            UUID employeeId, String financialYear, List<PreTaxDeductionRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);
        DeductionRules.validatePreTaxDeductions(requests);

        preTaxDeductionRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        preTaxDeductionRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvPreTaxDeduction> entities = requests.stream()
                    .map(req -> new EmployeeInvPreTaxDeduction(tenantId, decl.getId(), req.kind(), req.amount()))
                    .toList();
            preTaxDeductionRepository.saveAll(entities);
        }

        return readForDeclaration(tenantId, decl.getId());
    }

    private DeductionDeclarationResponse replacePrevEmploymentInternal(
            UUID employeeId,
            String financialYear,
            List<PrevEmploymentRequest> requests,
            boolean ignoreWindow,
            EnteredBy enteredBy) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);

        List<EmployeeInvPrevEmployment> existing =
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, decl.getId());
        if (enteredBy == EnteredBy.EMPLOYEE && existing.stream().anyMatch(e -> e.getEnteredBy() == EnteredBy.OFFICER)) {
            throw new DeclarationNotEditableException(
                    "OFFICER_ENTERED",
                    "Previous employment details entered by payroll officer cannot be edited in self-service");
        }

        DeductionRules.validatePrevEmployment(requests);

        prevEmploymentRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        prevEmploymentRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvPrevEmployment> entities = requests.stream()
                    .map(req -> new EmployeeInvPrevEmployment(
                            tenantId,
                            decl.getId(),
                            req.kind(),
                            req.amount(),
                            req.employerName(),
                            DeductionRules.normaliseTan(req.employerTan()),
                            enteredBy))
                    .toList();
            prevEmploymentRepository.saveAll(entities);
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

    private DeductionDeclarationResponse readForDeclaration(UUID tenantId, UUID declarationId) {
        List<EmployeeInvSection6A> s6aList =
                section6ARepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        List<EmployeeInvPreTaxDeduction> preTaxList =
                preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        List<EmployeeInvPrevEmployment> prevEmpList =
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);

        List<Section6ALineResponse> s6aResponses = new ArrayList<>();
        for (EmployeeInvSection6A entity : s6aList) {
            String sectionCode = "";
            String name = "";
            var optItem = section6AItemReader.findById(entity.getSection6aItemId());
            if (optItem.isPresent()) {
                sectionCode = optItem.get().sectionCode();
                name = optItem.get().name();
            }
            s6aResponses.add(new Section6ALineResponse(
                    entity.getId(),
                    entity.getSection6aItemId(),
                    sectionCode,
                    name,
                    entity.getDescription(),
                    entity.getAmount()));
        }

        List<PreTaxDeductionResponse> preTaxResponses = preTaxList.stream()
                .map(p -> new PreTaxDeductionResponse(p.getId(), p.getKind(), p.getAmount()))
                .toList();

        List<PrevEmploymentResponse> prevEmpResponses = prevEmpList.stream()
                .map(p -> new PrevEmploymentResponse(
                        p.getId(),
                        p.getKind(),
                        p.getAmount(),
                        p.getEmployerName(),
                        p.getEmployerTan(),
                        p.getEnteredBy()))
                .toList();

        return new DeductionDeclarationResponse(s6aResponses, preTaxResponses, prevEmpResponses);
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }
}
