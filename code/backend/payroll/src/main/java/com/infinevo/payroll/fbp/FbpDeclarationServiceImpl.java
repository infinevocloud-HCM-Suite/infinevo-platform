package com.infinevo.payroll.fbp;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.payroll.salary.CtcStructure;
import com.infinevo.payroll.salary.CtcStructureRepository;
import com.infinevo.payroll.salary.EmployeeEarning;
import com.infinevo.payroll.salary.EmployeeEarningRepository;
import com.infinevo.payroll.salary.EmployeeReimbursement;
import com.infinevo.payroll.salary.EmployeeReimbursementRepository;
import com.infinevo.payroll.salary.SalaryConflictException;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.payroll.salary.SalaryValidationException;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link FbpDeclarationService} (W-27.2).
 */
@Service
@Transactional
public class FbpDeclarationServiceImpl implements FbpDeclarationService {

    private final EmployeeFbpComponentRepository employeeFbpComponentRepository;
    private final CtcStructureRepository ctcStructureRepository;
    private final EmployeeEarningRepository employeeEarningRepository;
    private final EmployeeReimbursementRepository employeeReimbursementRepository;
    private final EarningRepository earningRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final FbpPlanService fbpPlanService;
    private final EmployeeService employeeService;

    public FbpDeclarationServiceImpl(
            EmployeeFbpComponentRepository employeeFbpComponentRepository,
            CtcStructureRepository ctcStructureRepository,
            EmployeeEarningRepository employeeEarningRepository,
            EmployeeReimbursementRepository employeeReimbursementRepository,
            EarningRepository earningRepository,
            ReimbursementRepository reimbursementRepository,
            FbpPlanService fbpPlanService,
            EmployeeService employeeService) {
        this.employeeFbpComponentRepository = Objects.requireNonNull(
                employeeFbpComponentRepository, "employeeFbpComponentRepository must not be null");
        this.ctcStructureRepository =
                Objects.requireNonNull(ctcStructureRepository, "ctcStructureRepository must not be null");
        this.employeeEarningRepository =
                Objects.requireNonNull(employeeEarningRepository, "employeeEarningRepository must not be null");
        this.employeeReimbursementRepository = Objects.requireNonNull(
                employeeReimbursementRepository, "employeeReimbursementRepository must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.reimbursementRepository =
                Objects.requireNonNull(reimbursementRepository, "reimbursementRepository must not be null");
        this.fbpPlanService = Objects.requireNonNull(fbpPlanService, "fbpPlanService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public FbpDeclarationResponse readOwn() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));

        return getDeclaration(tenantId, employee.id(), LocalDate.now());
    }

    @Override
    public FbpDeclarationResponse declareOwn(FbpDeclarationRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));

        LocalDate today = LocalDate.now();
        if (!fbpPlanService.isWindowOpen(today)) {
            throw new SalaryConflictException("WINDOW_CLOSED: FBP declaration window is closed");
        }

        return saveDeclaration(tenantId, employee.id(), today, request, DeclaredBy.EMPLOYEE);
    }

    @Override
    @Transactional(readOnly = true)
    public FbpDeclarationResponse read(UUID employeeId, LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        LocalDate targetDate = asOf != null ? asOf : LocalDate.now();
        return getDeclaration(tenantId, employeeId, targetDate);
    }

    @Override
    public FbpDeclarationResponse set(UUID employeeId, FbpDeclarationRequest request) {
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        LocalDate today = LocalDate.now();
        return saveDeclaration(tenantId, employeeId, today, request, DeclaredBy.OFFICER);
    }

    @Override
    public void carryForward(UUID oldVersionId, UUID newVersionId, UUID tenantId, UUID employeeId) {
        if (oldVersionId == null || newVersionId == null) {
            return;
        }

        List<EmployeeFbpComponent> oldDeclarations =
                employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(tenantId, oldVersionId);
        if (oldDeclarations.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> oldAmounts = new HashMap<>();
        for (EmployeeFbpComponent decl : oldDeclarations) {
            if (decl.getEarningId() != null) {
                oldAmounts.put("EARNING:" + decl.getEarningId(), decl.getAnnualAmount());
            } else if (decl.getReimbursementId() != null) {
                oldAmounts.put("REIMBURSEMENT:" + decl.getReimbursementId(), decl.getAnnualAmount());
            }
        }

        List<FbpStructureLine> newFbpLines = loadFbpLines(tenantId, newVersionId);
        Instant now = Instant.now();
        String actor = currentActor();

        for (FbpStructureLine newLine : newFbpLines) {
            String key = newLine.kind() + ":" + newLine.componentId();
            BigDecimal previousDeclared = oldAmounts.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal carriedAmount = previousDeclared.min(newLine.lineAnnualAmount());
            BigDecimal monthlyAmount =
                    Money.of(carriedAmount).divide(BigDecimal.valueOf(12)).raw();

            UUID earningId = "EARNING".equalsIgnoreCase(newLine.kind()) ? newLine.componentId() : null;
            UUID reimbursementId = "REIMBURSEMENT".equalsIgnoreCase(newLine.kind()) ? newLine.componentId() : null;

            EmployeeFbpComponent carried = new EmployeeFbpComponent(
                    tenantId,
                    newVersionId,
                    employeeId,
                    earningId,
                    reimbursementId,
                    carriedAmount,
                    monthlyAmount,
                    now,
                    DeclaredBy.CARRIED,
                    actor);
            employeeFbpComponentRepository.save(carried);
        }
    }

    @Override
    public void recapAfterSalaryUpdate(UUID ctcStructureId, UUID tenantId) {
        if (ctcStructureId == null || tenantId == null) {
            return;
        }

        List<EmployeeFbpComponent> existingDeclarations =
                employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(tenantId, ctcStructureId);
        if (existingDeclarations.isEmpty()) {
            return;
        }

        List<FbpStructureLine> newFbpLines = loadFbpLines(tenantId, ctcStructureId);
        Map<String, FbpStructureLine> lineMap = new HashMap<>();
        for (FbpStructureLine line : newFbpLines) {
            lineMap.put(line.kind().toUpperCase() + ":" + line.componentId(), line);
        }

        Set<String> existingKeys = new HashSet<>();
        for (EmployeeFbpComponent decl : existingDeclarations) {
            String key = (decl.getEarningId() != null
                    ? "EARNING:" + decl.getEarningId()
                    : "REIMBURSEMENT:" + decl.getReimbursementId());
            existingKeys.add(key);
            FbpStructureLine matchingLine = lineMap.get(key);
            if (matchingLine == null) {
                employeeFbpComponentRepository.delete(decl);
            } else if (decl.getAnnualAmount().compareTo(matchingLine.lineAnnualAmount()) > 0) {
                BigDecimal cappedAnnual = matchingLine.lineAnnualAmount();
                BigDecimal monthlyAmount =
                        Money.of(cappedAnnual).divide(BigDecimal.valueOf(12)).raw();
                decl.setAnnualAmount(cappedAnnual);
                decl.setMonthlyAmount(monthlyAmount);
                employeeFbpComponentRepository.save(decl);
            }
        }

        CtcStructure version = ctcStructureRepository
                .findByIdAndTenantId(ctcStructureId, tenantId)
                .orElse(null);
        UUID employeeId = version != null ? version.getEmployeeId() : null;
        if (employeeId != null) {
            Instant now = Instant.now();
            String actor = currentActor();
            for (FbpStructureLine line : newFbpLines) {
                String key = line.kind().toUpperCase() + ":" + line.componentId();
                if (!existingKeys.contains(key)) {
                    UUID earningId = "EARNING".equalsIgnoreCase(line.kind()) ? line.componentId() : null;
                    UUID reimbursementId = "REIMBURSEMENT".equalsIgnoreCase(line.kind()) ? line.componentId() : null;
                    EmployeeFbpComponent entity = new EmployeeFbpComponent(
                            tenantId,
                            ctcStructureId,
                            employeeId,
                            earningId,
                            reimbursementId,
                            BigDecimal.ZERO.setScale(4),
                            BigDecimal.ZERO.setScale(4),
                            now,
                            DeclaredBy.CARRIED,
                            actor);
                    employeeFbpComponentRepository.save(entity);
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FbpSummaryResponse summary(UUID ctcStructureId, UUID tenantId) {
        List<FbpStructureLine> fbpLines = loadFbpLines(tenantId, ctcStructureId);
        BigDecimal poolAnnual = BigDecimal.ZERO;
        for (FbpStructureLine line : fbpLines) {
            poolAnnual = poolAnnual.add(line.lineAnnualAmount());
        }

        List<EmployeeFbpComponent> declaredRows =
                employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(tenantId, ctcStructureId);
        BigDecimal declaredAnnual = BigDecimal.ZERO;
        for (EmployeeFbpComponent decl : declaredRows) {
            declaredAnnual = declaredAnnual.add(decl.getAnnualAmount());
        }

        BigDecimal unallocatedAnnual = poolAnnual.subtract(declaredAnnual);
        return new FbpSummaryResponse(
                Money.of(poolAnnual).toAmount(),
                Money.of(declaredAnnual).toAmount(),
                Money.of(unallocatedAnnual).toAmount());
    }

    private FbpDeclarationResponse getDeclaration(UUID tenantId, UUID employeeId, LocalDate date) {
        CtcStructure version = ctcStructureRepository
                .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        tenantId, employeeId, date)
                .orElseThrow(() -> new SalaryNotFoundException(
                        "No salary structure version in force as of " + date + " for employee " + employeeId));

        boolean windowOpen = fbpPlanService.isWindowOpen(date);
        List<FbpStructureLine> fbpLines = loadFbpLines(tenantId, version.getId());

        List<EmployeeFbpComponent> declaredList =
                employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(tenantId, version.getId());
        Map<String, EmployeeFbpComponent> declaredMap = new HashMap<>();
        Instant declaredAt = null;
        String declaredBy = null;

        for (EmployeeFbpComponent decl : declaredList) {
            if (decl.getEarningId() != null) {
                declaredMap.put("EARNING:" + decl.getEarningId(), decl);
            } else if (decl.getReimbursementId() != null) {
                declaredMap.put("REIMBURSEMENT:" + decl.getReimbursementId(), decl);
            }
            if (declaredAt == null || decl.getDeclaredAt().isAfter(declaredAt)) {
                declaredAt = decl.getDeclaredAt();
                declaredBy = decl.getDeclaredBy().name();
            }
        }

        BigDecimal poolAnnual = BigDecimal.ZERO;
        BigDecimal declaredAnnual = BigDecimal.ZERO;
        List<FbpDeclarationLineResponse> lines = new ArrayList<>(fbpLines.size());

        for (FbpStructureLine line : fbpLines) {
            poolAnnual = poolAnnual.add(line.lineAnnualAmount());
            String key = line.kind() + ":" + line.componentId();
            EmployeeFbpComponent declared = declaredMap.get(key);

            BigDecimal lineDeclaredAnnual = declared != null ? declared.getAnnualAmount() : BigDecimal.ZERO.setScale(4);
            BigDecimal lineDeclaredMonthly =
                    declared != null ? declared.getMonthlyAmount() : BigDecimal.ZERO.setScale(4);
            declaredAnnual = declaredAnnual.add(lineDeclaredAnnual);

            lines.add(new FbpDeclarationLineResponse(
                    line.kind(),
                    line.componentId(),
                    line.componentCode(),
                    line.componentName(),
                    line.lineAnnualAmount(),
                    lineDeclaredAnnual,
                    lineDeclaredMonthly));
        }

        BigDecimal unallocatedAnnual = poolAnnual.subtract(declaredAnnual);
        FbpSummaryResponse summary = new FbpSummaryResponse(
                Money.of(poolAnnual).toAmount(),
                Money.of(declaredAnnual).toAmount(),
                Money.of(unallocatedAnnual).toAmount());

        return new FbpDeclarationResponse(
                version.getId(), employeeId, windowOpen, declaredAt, declaredBy, summary, lines);
    }

    private FbpDeclarationResponse saveDeclaration(
            UUID tenantId, UUID employeeId, LocalDate date, FbpDeclarationRequest request, DeclaredBy actorType) {
        if (request == null) {
            throw new SalaryValidationException("lines", "Request body must not be null");
        }

        CtcStructure version = ctcStructureRepository
                .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        tenantId, employeeId, date)
                .orElseThrow(() -> new SalaryNotFoundException(
                        "No salary structure version in force as of " + date + " for employee " + employeeId));

        List<FbpStructureLine> fbpLines = loadFbpLines(tenantId, version.getId());
        Map<String, FbpStructureLine> availableMap = new HashMap<>();
        for (FbpStructureLine line : fbpLines) {
            availableMap.put(line.kind().toUpperCase() + ":" + line.componentId(), line);
        }

        List<FbpDeclarationLineRequest> requestedLines =
                request.lines() != null ? request.lines() : Collections.emptyList();
        Set<String> seen = new HashSet<>();
        Map<String, BigDecimal> amountsToSave = new HashMap<>();

        for (FbpDeclarationLineRequest reqLine : requestedLines) {
            if (reqLine.kind() == null || reqLine.componentId() == null) {
                throw new SalaryValidationException("lines", "Each declaration line must specify kind and componentId");
            }
            String key = reqLine.kind().trim().toUpperCase() + ":" + reqLine.componentId();
            if (!seen.add(key)) {
                throw new SalaryValidationException(
                        "lines", "Duplicate declaration line for component " + reqLine.componentId());
            }
            FbpStructureLine structureLine = availableMap.get(key);
            if (structureLine == null) {
                throw new SalaryValidationException(
                        "lines",
                        "Component " + reqLine.componentId() + " (" + reqLine.kind()
                                + ") is not an active FBP component in the employee's salary version");
            }
            if (reqLine.annualAmount() == null || reqLine.annualAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new SalaryValidationException("annualAmount", "Annual amount must be non-negative");
            }
            if (reqLine.annualAmount().compareTo(structureLine.lineAnnualAmount()) > 0) {
                throw new SalaryValidationException(
                        "annualAmount",
                        "Declared amount " + reqLine.annualAmount() + " exceeds line ceiling "
                                + structureLine.lineAnnualAmount() + " for component " + reqLine.componentId());
            }
            amountsToSave.put(key, reqLine.annualAmount());
        }

        employeeFbpComponentRepository.deleteAllByTenantIdAndCtcStructureId(tenantId, version.getId());

        Instant now = Instant.now();
        String actor = currentActor();
        List<EmployeeFbpComponent> savedEntities = new ArrayList<>(fbpLines.size());

        for (FbpStructureLine line : fbpLines) {
            String key = line.kind().toUpperCase() + ":" + line.componentId();
            BigDecimal annualAmount = amountsToSave.getOrDefault(key, BigDecimal.ZERO.setScale(4));
            BigDecimal monthlyAmount =
                    Money.of(annualAmount).divide(BigDecimal.valueOf(12)).raw();

            UUID earningId = "EARNING".equalsIgnoreCase(line.kind()) ? line.componentId() : null;
            UUID reimbursementId = "REIMBURSEMENT".equalsIgnoreCase(line.kind()) ? line.componentId() : null;

            EmployeeFbpComponent entity = new EmployeeFbpComponent(
                    tenantId,
                    version.getId(),
                    employeeId,
                    earningId,
                    reimbursementId,
                    annualAmount,
                    monthlyAmount,
                    now,
                    actorType,
                    actor);
            savedEntities.add(employeeFbpComponentRepository.save(entity));
        }

        return getDeclaration(tenantId, employeeId, date);
    }

    private List<FbpStructureLine> loadFbpLines(UUID tenantId, UUID ctcStructureId) {
        List<EmployeeEarning> earnings =
                employeeEarningRepository.findAllByTenantIdAndCtcStructureId(tenantId, ctcStructureId);
        List<EmployeeReimbursement> reimbursements =
                employeeReimbursementRepository.findAllByTenantIdAndCtcStructureId(tenantId, ctcStructureId);

        List<FbpStructureLine> result = new ArrayList<>();

        for (EmployeeEarning ee : earnings) {
            Optional<Earning> def = earningRepository.findByIdAndTenantIdAndDeletedFalse(ee.getComponentId(), tenantId);
            if (def.isPresent() && def.get().isFbpComponent() && def.get().isActive()) {
                result.add(new FbpStructureLine(
                        "EARNING",
                        ee.getComponentId(),
                        def.get().getCode(),
                        def.get().getName(),
                        ee.getAnnualAmount()));
            }
        }

        for (EmployeeReimbursement er : reimbursements) {
            Optional<Reimbursement> def =
                    reimbursementRepository.findByIdAndTenantIdAndDeletedFalse(er.getComponentId(), tenantId);
            if (def.isPresent() && def.get().isFbpComponent() && def.get().isActive()) {
                result.add(new FbpStructureLine(
                        "REIMBURSEMENT",
                        er.getComponentId(),
                        def.get().getCode(),
                        def.get().getName(),
                        er.getAnnualAmount()));
            }
        }

        return result;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            String name = auth.getName();
            return name.length() > 100 ? name.substring(0, 100) : name;
        }
        return EmployeeFbpComponent.ACTOR_SYSTEM;
    }

    private record FbpStructureLine(
            String kind, UUID componentId, String componentCode, String componentName, BigDecimal lineAnnualAmount) {}
}
