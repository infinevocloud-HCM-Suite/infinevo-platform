package com.infinevo.payroll.taxdeclaration.housing;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclarationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.HousingDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyResponse;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HousingDeclarationService} (W-32.2).
 */
@Service
@Transactional
public class HousingDeclarationServiceImpl implements HousingDeclarationService {

    private final EmployeeInvestmentDeclarationRepository declarationRepository;
    private final TaxDeclarationService taxDeclarationService;
    private final EmployeeInvHouseRentRepository houseRentRepository;
    private final EmployeeInvHomeLoanRepository homeLoanRepository;
    private final EmployeeInvLetOutPropertyRepository letOutPropertyRepository;
    private final EmployeeInvLetOutPropertyLineRepository letOutPropertyLineRepository;
    private final TaxDeclarationWindowService windowService;
    private final EmployeeService employeeService;
    private final HraRuleReader hraRuleReader;
    private final LetOutPropertyRuleReader letOutPropertyRuleReader;
    private final Clock clock;

    @Autowired
    public HousingDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationService taxDeclarationService,
            EmployeeInvHouseRentRepository houseRentRepository,
            EmployeeInvHomeLoanRepository homeLoanRepository,
            EmployeeInvLetOutPropertyRepository letOutPropertyRepository,
            EmployeeInvLetOutPropertyLineRepository letOutPropertyLineRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader,
            LetOutPropertyRuleReader letOutPropertyRuleReader) {
        this(
                declarationRepository,
                taxDeclarationService,
                houseRentRepository,
                homeLoanRepository,
                letOutPropertyRepository,
                letOutPropertyLineRepository,
                windowService,
                employeeService,
                hraRuleReader,
                letOutPropertyRuleReader,
                TaxDeclarationRules.defaultClock());
    }

    HousingDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationService taxDeclarationService,
            EmployeeInvHouseRentRepository houseRentRepository,
            EmployeeInvHomeLoanRepository homeLoanRepository,
            EmployeeInvLetOutPropertyRepository letOutPropertyRepository,
            EmployeeInvLetOutPropertyLineRepository letOutPropertyLineRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader,
            LetOutPropertyRuleReader letOutPropertyRuleReader,
            Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.declarationRepository =
                Objects.requireNonNull(declarationRepository, "declarationRepository must not be null");
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.houseRentRepository = Objects.requireNonNull(houseRentRepository, "houseRentRepository must not be null");
        this.homeLoanRepository = Objects.requireNonNull(homeLoanRepository, "homeLoanRepository must not be null");
        this.letOutPropertyRepository =
                Objects.requireNonNull(letOutPropertyRepository, "letOutPropertyRepository must not be null");
        this.letOutPropertyLineRepository =
                Objects.requireNonNull(letOutPropertyLineRepository, "letOutPropertyLineRepository must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.hraRuleReader = Objects.requireNonNull(hraRuleReader, "hraRuleReader must not be null");
        this.letOutPropertyRuleReader =
                Objects.requireNonNull(letOutPropertyRuleReader, "letOutPropertyRuleReader must not be null");
    }

    @Override
    @Transactional
    public HousingDeclarationResponse readOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return read(current.id(), financialYear);
    }

    @Override
    @Transactional
    public HousingDeclarationResponse read(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());
        return readForDeclaration(tenantId, decl.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public HousingDeclarationResponse readByDeclarationId(UUID declarationId) {
        UUID tenantId = TenantContext.require();
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(declarationId);
        return readForDeclaration(tenantId, decl.getId());
    }

    @Override
    public HousingDeclarationResponse replaceHouseRentOwn(String financialYear, List<HouseRentRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replaceHouseRentInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public HousingDeclarationResponse replaceHouseRent(
            UUID employeeId, String financialYear, List<HouseRentRequest> requests) {
        return replaceHouseRentInternal(employeeId, financialYear, requests, true);
    }

    @Override
    public HousingDeclarationResponse replaceHomeLoansOwn(String financialYear, List<HomeLoanRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replaceHomeLoansInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public HousingDeclarationResponse replaceHomeLoans(
            UUID employeeId, String financialYear, List<HomeLoanRequest> requests) {
        return replaceHomeLoansInternal(employeeId, financialYear, requests, true);
    }

    @Override
    public HousingDeclarationResponse replaceLetOutPropertiesOwn(
            String financialYear, List<LetOutPropertyRequest> requests) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return replaceLetOutPropertiesInternal(current.id(), financialYear, requests, false);
    }

    @Override
    public HousingDeclarationResponse replaceLetOutProperties(
            UUID employeeId, String financialYear, List<LetOutPropertyRequest> requests) {
        return replaceLetOutPropertiesInternal(employeeId, financialYear, requests, true);
    }

    private HousingDeclarationResponse replaceHouseRentInternal(
            UUID employeeId, String financialYear, List<HouseRentRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        // The reference read is only needed when there are rows to check against the threshold.
        boolean checkPan = window.isPanRequiredForRentOverThreshold() && requests != null && !requests.isEmpty();
        BigDecimal panThreshold =
                checkPan ? hraRuleReader.getPanMandatoryThreshold(fy.label(), decl.getTaxRegime()) : null;
        HousingRules.validateHouseRent(requests, fy, checkPan, panThreshold);

        houseRentRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        houseRentRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvHouseRent> entities = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                HouseRentRequest req = requests.get(i);
                LocalDate from = HousingRules.parseYearMonth(req.fromMonth(), "from_month", i + 1);
                LocalDate to = HousingRules.parseYearMonth(req.toMonth(), "to_month", i + 1);
                entities.add(new EmployeeInvHouseRent(
                        tenantId,
                        decl.getId(),
                        from,
                        to,
                        req.address(),
                        req.landlordName(),
                        req.landlordPan(),
                        Boolean.TRUE.equals(req.isMetro()),
                        req.amountPerMonth()));
            }
            houseRentRepository.saveAll(entities);
        }

        decl.setStayingInRentedHouse(requests != null && !requests.isEmpty());
        declarationRepository.save(decl);

        return readForDeclaration(tenantId, decl.getId());
    }

    private HousingDeclarationResponse replaceHomeLoansInternal(
            UUID employeeId, String financialYear, List<HomeLoanRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);
        HousingRules.validateHomeLoans(requests);

        homeLoanRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        homeLoanRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            List<EmployeeInvHomeLoan> entities = requests.stream()
                    .map(req -> new EmployeeInvHomeLoan(
                            tenantId,
                            decl.getId(),
                            req.lenderName(),
                            req.lenderPan(),
                            req.principalPaid(),
                            req.interestPaid(),
                            Boolean.TRUE.equals(req.isFirstTimeBuyer()),
                            req.loanSanctionedOn()))
                    .toList();
            homeLoanRepository.saveAll(entities);
        }

        decl.setRepayingSelfOccupiedLoan(requests != null && !requests.isEmpty());
        declarationRepository.save(decl);

        return readForDeclaration(tenantId, decl.getId());
    }

    private HousingDeclarationResponse replaceLetOutPropertiesInternal(
            UUID employeeId, String financialYear, List<LetOutPropertyRequest> requests, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        validateEditable(decl, ignoreWindow);
        HousingRules.validateLetOutProperties(requests);

        letOutPropertyLineRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        letOutPropertyLineRepository.flush();
        letOutPropertyRepository.deleteByTenantIdAndDeclarationId(tenantId, decl.getId());
        letOutPropertyRepository.flush();

        if (requests != null && !requests.isEmpty()) {
            BigDecimal stdDedPct =
                    letOutPropertyRuleReader.getStandardDeductionPercent(fy.label(), decl.getTaxRegime());

            for (LetOutPropertyRequest propReq : requests) {
                BigDecimal annualRent = BigDecimal.ZERO;
                BigDecimal municipalTax = BigDecimal.ZERO;
                BigDecimal loanInterest = BigDecimal.ZERO;

                if (propReq.lines() != null) {
                    for (LetOutPropertyLineRequest line : propReq.lines()) {
                        if (line.lineType() == LetOutPropertyLineType.ANNUAL_RENT && line.amount() != null) {
                            annualRent = line.amount();
                        } else if (line.lineType() == LetOutPropertyLineType.MUNICIPAL_TAX && line.amount() != null) {
                            municipalTax = line.amount();
                        } else if (line.lineType() == LetOutPropertyLineType.LOAN_INTEREST && line.amount() != null) {
                            loanInterest = line.amount();
                        }
                    }
                }

                BigDecimal netIncomeLoss =
                        HousingRules.calculateNetIncomeLoss(annualRent, municipalTax, loanInterest, stdDedPct);
                EmployeeInvLetOutProperty prop = new EmployeeInvLetOutProperty(
                        tenantId, decl.getId(), propReq.propertyName(), propReq.address(), netIncomeLoss);
                prop = letOutPropertyRepository.save(prop);

                if (propReq.lines() != null && !propReq.lines().isEmpty()) {
                    final UUID propId = prop.getId();
                    List<EmployeeInvLetOutPropertyLine> lines = propReq.lines().stream()
                            .map(lineReq -> new EmployeeInvLetOutPropertyLine(
                                    tenantId,
                                    decl.getId(),
                                    propId,
                                    lineReq.lineType(),
                                    lineReq.amount(),
                                    lineReq.lenderName(),
                                    lineReq.lenderPan()))
                            .toList();
                    letOutPropertyLineRepository.saveAll(lines);
                }
            }
        }

        decl.setHasLetOutProperty(requests != null && !requests.isEmpty());
        declarationRepository.save(decl);

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

    private HousingDeclarationResponse readForDeclaration(UUID tenantId, UUID declarationId) {
        List<EmployeeInvHouseRent> rentList =
                houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId);
        List<EmployeeInvHomeLoan> loanList = homeLoanRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        List<EmployeeInvLetOutProperty> propList =
                letOutPropertyRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);

        List<HouseRentResponse> rentResponses = rentList.stream()
                .map(r -> new HouseRentResponse(
                        r.getId(),
                        YearMonth.from(r.getFromMonth()).toString(),
                        YearMonth.from(r.getToMonth()).toString(),
                        r.getAddress(),
                        r.getLandlordName(),
                        r.getLandlordPan(),
                        r.isMetro(),
                        r.getAmountPerMonth()))
                .toList();

        List<HomeLoanResponse> loanResponses = loanList.stream()
                .map(l -> new HomeLoanResponse(
                        l.getId(),
                        l.getLenderName(),
                        l.getLenderPan(),
                        l.getPrincipalPaid(),
                        l.getInterestPaid(),
                        l.isFirstTimeBuyer(),
                        l.getLoanSanctionedOn()))
                .toList();

        Map<UUID, List<EmployeeInvLetOutPropertyLine>> linesByPropId;
        if (propList.isEmpty()) {
            linesByPropId = Collections.emptyMap();
        } else {
            List<EmployeeInvLetOutPropertyLine> allLines =
                    letOutPropertyLineRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
            linesByPropId =
                    allLines.stream().collect(Collectors.groupingBy(EmployeeInvLetOutPropertyLine::getPropertyId));
        }

        List<LetOutPropertyResponse> propResponses = propList.stream()
                .map(p -> {
                    List<EmployeeInvLetOutPropertyLine> lines =
                            linesByPropId.getOrDefault(p.getId(), Collections.emptyList());
                    List<LetOutPropertyLineResponse> lineResponses = lines.stream()
                            .map(line -> new LetOutPropertyLineResponse(
                                    line.getId(),
                                    line.getLineType(),
                                    line.getAmount(),
                                    line.getLenderName(),
                                    line.getLenderPan()))
                            .toList();
                    return new LetOutPropertyResponse(
                            p.getId(), p.getPropertyName(), p.getAddress(), p.getNetIncomeLoss(), lineResponses);
                })
                .toList();

        return new HousingDeclarationResponse(rentResponses, loanResponses, propResponses);
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }
}
