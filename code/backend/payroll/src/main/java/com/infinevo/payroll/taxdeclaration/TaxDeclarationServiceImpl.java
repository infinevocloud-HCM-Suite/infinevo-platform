package com.infinevo.payroll.taxdeclaration;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.HraRuleReader;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaxDeclarationService} (W-32.1).
 */
@Service
@Transactional
public class TaxDeclarationServiceImpl implements TaxDeclarationService {

    private final EmployeeInvestmentDeclarationRepository declarationRepository;
    private final TaxDeclarationWindowService windowService;
    private final EmployeeService employeeService;
    private final HraRuleReader hraRuleReader;
    private final Clock clock;
    private final ProofInProgressCheck proofInProgressCheck;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public TaxDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader) {
        this(declarationRepository, windowService, employeeService, hraRuleReader, TaxDeclarationRules.defaultClock());
    }

    TaxDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader,
            Clock clock) {
        this(
                declarationRepository,
                windowService,
                employeeService,
                hraRuleReader,
                clock,
                ProofInProgressCheck.NONE,
                null);
    }

    /** The constructor Spring uses: the proof check is a required part of the reopen rule (W-34.1). */
    @Autowired
    public TaxDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader,
            ProofInProgressCheck proofInProgressCheck,
            org.springframework.context.ApplicationEventPublisher publisher) {
        this(
                declarationRepository,
                windowService,
                employeeService,
                hraRuleReader,
                TaxDeclarationRules.defaultClock(),
                proofInProgressCheck,
                publisher);
    }

    TaxDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationWindowService windowService,
            EmployeeService employeeService,
            HraRuleReader hraRuleReader,
            Clock clock,
            ProofInProgressCheck proofInProgressCheck,
            org.springframework.context.ApplicationEventPublisher publisher) {
        this.proofInProgressCheck =
                Objects.requireNonNull(proofInProgressCheck, "proofInProgressCheck must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.declarationRepository =
                Objects.requireNonNull(declarationRepository, "declarationRepository must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.hraRuleReader = Objects.requireNonNull(hraRuleReader, "hraRuleReader must not be null");
        this.publisher = publisher;
    }

    @Override
    public TaxDeclarationResponse readOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return read(current.id(), financialYear);
    }

    @Override
    public TaxDeclarationResponse saveOwn(String financialYear, TaxDeclarationRequest request) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return saveInternal(current.id(), financialYear, request, false);
    }

    @Override
    public TaxDeclarationResponse submitOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return submitInternal(current.id(), financialYear, false);
    }

    @Override
    public TaxDeclarationResponse reopenOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        return reopenInternal(current.id(), financialYear, false, true);
    }

    @Override
    public TaxDeclarationResponse read(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseGet(() -> {
                    IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
                    EmployeeInvestmentDeclaration newDecl = new EmployeeInvestmentDeclaration(
                            tenantId, employeeId, fy.label(), window.getDefaultTaxRegime());
                    return declarationRepository.save(newDecl);
                });
        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        return toResponse(decl, window, TaxDeclarationRules.today(clock));
    }

    @Override
    public TaxDeclarationResponse save(UUID employeeId, String financialYear, TaxDeclarationRequest request) {
        return saveInternal(employeeId, financialYear, request, true);
    }

    @Override
    public TaxDeclarationResponse submit(UUID employeeId, String financialYear) {
        return submitInternal(employeeId, financialYear, true);
    }

    @Override
    public TaxDeclarationResponse reopen(UUID employeeId, String financialYear) {
        return reopenInternal(employeeId, financialYear, true, false);
    }

    @Override
    public TaxDeclarationResponse lock(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseGet(() -> {
                    IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
                    EmployeeInvestmentDeclaration newDecl = new EmployeeInvestmentDeclaration(
                            tenantId, employeeId, fy.label(), window.getDefaultTaxRegime());
                    return declarationRepository.save(newDecl);
                });

        decl.setLocked(true);
        decl.setLockedAt(Instant.now());
        decl = declarationRepository.save(decl);

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        return toResponse(decl, window, TaxDeclarationRules.today(clock));
    }

    @Override
    public TaxDeclarationResponse unlock(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseThrow(() -> new DeclarationNotFoundException(employeeId, fy.label()));

        decl.setLocked(false);
        decl.setLockedAt(null);
        decl = declarationRepository.save(decl);

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        return toResponse(decl, window, TaxDeclarationRules.today(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean editable(UUID declarationId) {
        return editable(declarationId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean editable(UUID declarationId, boolean ignoreWindow) {
        UUID tenantId = TenantContext.require();
        return declarationRepository
                .findByTenantIdAndId(tenantId, declarationId)
                .map(decl -> {
                    IncomeTaxDeclarationWindow window =
                            windowService.findOrCreateDefault(tenantId, decl.getFinancialYear());
                    return TaxDeclarationRules.isEditable(decl, window, TaxDeclarationRules.today(clock), ignoreWindow);
                })
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeInvestmentDeclaration require(UUID declarationId) {
        UUID tenantId = TenantContext.require();
        return declarationRepository
                .findByTenantIdAndId(tenantId, declarationId)
                .orElseThrow(() -> new DeclarationNotFoundException(declarationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeInvestmentDeclaration> find(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        return declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeInvestmentDeclaration require(UUID employeeId, String financialYear) {
        return find(employeeId, financialYear)
                .orElseThrow(() -> new DeclarationNotFoundException(employeeId, financialYear));
    }

    private TaxDeclarationResponse saveInternal(
            UUID employeeId, String financialYear, TaxDeclarationRequest request, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseGet(() -> {
                    IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
                    EmployeeInvestmentDeclaration newDecl = new EmployeeInvestmentDeclaration(
                            tenantId, employeeId, fy.label(), window.getDefaultTaxRegime());
                    return declarationRepository.save(newDecl);
                });

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        LocalDate today = TaxDeclarationRules.today(clock);

        if (!TaxDeclarationRules.isEditable(decl, window, today, ignoreWindow)) {
            throw new DeclarationNotEditableException(
                    "NOT_EDITABLE", TaxDeclarationRules.notEditableReason(decl, window, today, ignoreWindow));
        }

        if (request != null) {
            if (request.taxRegime() != null) {
                String newRegime = request.taxRegime().toUpperCase();
                if (!newRegime.equals("OLD") && !newRegime.equals("NEW")) {
                    throw new WindowValidationException("Tax regime must be OLD or NEW");
                }
                if (!newRegime.equals(decl.getTaxRegime())) {
                    if (!window.isCanChangeTaxRegime()) {
                        throw new DeclarationNotEditableException(
                                "REGIME_CHANGE_NOT_ALLOWED", "Changing tax regime is not permitted by window settings");
                    }
                    decl.setTaxRegime(newRegime);
                }
            }
            if (request.isStayingInRentedHouse() != null) {
                decl.setStayingInRentedHouse(request.isStayingInRentedHouse());
            }
            if (request.isRepayingSelfOccupiedLoan() != null) {
                decl.setRepayingSelfOccupiedLoan(request.isRepayingSelfOccupiedLoan());
            }
            if (request.hasLetOutProperty() != null) {
                decl.setHasLetOutProperty(request.hasLetOutProperty());
            }
        }

        decl = declarationRepository.save(decl);
        return toResponse(decl, window, today);
    }

    private TaxDeclarationResponse submitInternal(UUID employeeId, String financialYear, boolean ignoreWindow) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseGet(() -> {
                    IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
                    EmployeeInvestmentDeclaration newDecl = new EmployeeInvestmentDeclaration(
                            tenantId, employeeId, fy.label(), window.getDefaultTaxRegime());
                    return declarationRepository.save(newDecl);
                });

        if (decl.getStatus() == DeclarationStatus.SUBMITTED) {
            throw new DeclarationNotEditableException("ALREADY_SUBMITTED", "Declaration is already submitted");
        }
        if (decl.isLocked()) {
            throw new DeclarationNotEditableException("NOT_EDITABLE", "Tax declaration is locked");
        }

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        LocalDate today = TaxDeclarationRules.today(clock);
        if (!ignoreWindow && !window.isOpenOn(today)) {
            throw new DeclarationNotEditableException("NOT_EDITABLE", "Tax declaration window is closed");
        }

        decl.setStatus(DeclarationStatus.SUBMITTED);
        decl.setSubmittedAt(Instant.now());
        decl = declarationRepository.save(decl);

        if (publisher != null) {
            publisher.publishEvent(new com.infinevo.payroll.taxcalc.recalc.event.DeclarationSubmittedEvent(
                    tenantId, employeeId, decl.getId(), fy.label()));
        }

        return toResponse(decl, window, today);
    }

    private TaxDeclarationResponse reopenInternal(
            UUID employeeId, String financialYear, boolean ignoreWindow, boolean guardProof) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = declarationRepository
                .findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label())
                .orElseThrow(() -> new DeclarationNotFoundException(employeeId, fy.label()));

        if (decl.isLocked()) {
            throw new DeclarationNotEditableException("LOCKED", "Declaration is locked");
        }
        // W-34.1: the employee cannot pull the declaration from under a proof that is being reviewed.
        // The officer's reopen is not guarded: it is how a mistake is put right. The check runs on both
        // paths all the same, because it takes the proof row's lock and so serialises this reopen with a
        // concurrent proof submit.
        boolean proofInProgress = proofInProgressCheck.isProofInProgress(tenantId, decl.getId());
        if (guardProof && proofInProgress) {
            throw new DeclarationNotEditableException(
                    "PROOF_IN_PROGRESS", "The proof of investment for this declaration is submitted or approved");
        }

        IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
        LocalDate today = TaxDeclarationRules.today(clock);
        if (!ignoreWindow && !window.isOpenOn(today)) {
            throw new DeclarationNotEditableException("WINDOW_CLOSED", "Window is closed for reopening");
        }

        decl.setStatus(DeclarationStatus.DRAFT);
        decl = declarationRepository.save(decl);
        return toResponse(decl, window, today);
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }

    private TaxDeclarationResponse toResponse(
            EmployeeInvestmentDeclaration decl, IncomeTaxDeclarationWindow window, LocalDate today) {
        boolean windowOpen = window != null && window.isOpenOn(today);
        boolean isEditable = TaxDeclarationRules.isEditable(decl, window, today, false);
        // Rent PAN rule: the same window flag and reference row HousingDeclarationServiceImpl enforces on save.
        boolean panRequired = window != null && window.isPanRequiredForRentOverThreshold();
        BigDecimal panThreshold = hraRuleReader.findPanMandatoryThreshold(decl.getFinancialYear(), decl.getTaxRegime());
        return new TaxDeclarationResponse(
                decl.getId(),
                decl.getEmployeeId(),
                decl.getFinancialYear(),
                decl.getTaxRegime(),
                decl.getStatus(),
                decl.isStayingInRentedHouse(),
                decl.isRepayingSelfOccupiedLoan(),
                decl.isHasLetOutProperty(),
                decl.isLocked(),
                decl.getSubmittedAt(),
                decl.getLockedAt(),
                windowOpen,
                isEditable,
                panRequired,
                panThreshold);
    }
}
