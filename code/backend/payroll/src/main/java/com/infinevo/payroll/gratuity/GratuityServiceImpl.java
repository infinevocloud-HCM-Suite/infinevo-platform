package com.infinevo.payroll.gratuity;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.taxcalc.EarningKinds;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link GratuityService} (W-91).
 */
@Service
@Transactional
public class GratuityServiceImpl implements GratuityService {

    private static final int MAX_ACTOR_LEN = 100;
    private static final Set<String> DA_CODES = Set.of("DA", "DEARNESS ALLOWANCE");

    private final GratuitySettlementRepository settlementRepository;
    private final EmployeeService employeeService;
    private final EmployeeSalaryService employeeSalaryService;

    public GratuityServiceImpl(
            GratuitySettlementRepository settlementRepository,
            EmployeeService employeeService,
            EmployeeSalaryService employeeSalaryService) {
        this.settlementRepository =
                Objects.requireNonNull(settlementRepository, "settlementRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeeSalaryService =
                Objects.requireNonNull(employeeSalaryService, "employeeSalaryService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public GratuityEstimateResponse estimateGratuity(GratuityEstimateRequest request) {
        if (request == null) {
            throw new GratuityValidationException("Request body must not be null", Map.of("request", "required"));
        }
        ComputedGratuity computed = computeForEmployee(request.employeeId(), request.exitDate(), request.reason());
        GratuityCalculator.Result result = computed.result();
        return new GratuityEstimateResponse(
                computed.employee().id(),
                computed.employee().dateOfJoining(),
                request.exitDate(),
                request.reason(),
                result.eligible(),
                result.exemptFrom5YrRule(),
                result.completedYears(),
                result.lastDrawnBasic(),
                result.lastDrawnDa(),
                result.calculatedAmount(),
                result.statutoryCeiling(),
                result.grossGratuityPayable(),
                result.taxExemptAmount(),
                result.taxableAmount());
    }

    @Override
    public GratuitySettlementResponse createSettlement(GratuitySettlementCreateRequest request) {
        if (request == null) {
            throw new GratuityValidationException("Request body must not be null", Map.of("request", "required"));
        }
        UUID tenantId = TenantContext.require();
        ComputedGratuity computed = computeForEmployee(request.employeeId(), request.exitDate(), request.reason());

        if (settlementRepository.existsByTenantIdAndEmployeeIdAndStatusNot(
                tenantId, request.employeeId(), GratuitySettlementStatus.CANCELLED)) {
            throw new GratuityConflictException(
                    "An active gratuity settlement already exists for employee " + request.employeeId());
        }

        GratuityCalculator.Result result = computed.result();
        String actor = currentActor();
        GratuitySettlement entity = new GratuitySettlement();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(request.employeeId());
        entity.setJoiningDate(computed.employee().dateOfJoining());
        entity.setExitDate(request.exitDate());
        entity.setCompletedYears(result.completedYears());
        entity.setExemptFrom5YrRule(result.exemptFrom5YrRule());
        entity.setLastDrawnBasic(result.lastDrawnBasic());
        entity.setLastDrawnDa(result.lastDrawnDa());
        entity.setCalculatedAmount(result.calculatedAmount());
        entity.setStatutoryCeiling(result.statutoryCeiling());
        entity.setGrossGratuityPayable(result.grossGratuityPayable());
        entity.setTaxExemptAmount(result.taxExemptAmount());
        entity.setTaxableAmount(result.taxableAmount());
        entity.setStatus(GratuitySettlementStatus.DRAFT);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);

        GratuitySettlement saved = settlementRepository.saveAndFlush(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GratuitySettlementResponse getSettlement(UUID id) {
        UUID tenantId = TenantContext.require();
        GratuitySettlement entity = settlementRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new GratuityNotFoundException("Gratuity settlement not found: " + id));
        return toResponse(entity);
    }

    private ComputedGratuity computeForEmployee(UUID employeeId, LocalDate exitDate, GratuityReason reason) {
        TenantContext.require();
        validateInputs(employeeId, exitDate, reason);

        EmployeeResponse employee = employeeService.get(employeeId);
        LocalDate joiningDate = employee.dateOfJoining();
        if (exitDate.isBefore(joiningDate)) {
            throw new GratuityValidationException(
                    "Exit date must not be before joining date",
                    Map.of("exitDate", "must be on or after employee joiningDate (" + joiningDate + ")"));
        }

        SalaryVersionResponse salary = employeeSalaryService.getAsOf(employeeId, exitDate);
        List<SalaryComponentItemResponse> earnings = salary.earnings() == null ? List.of() : salary.earnings();

        BigDecimal basic = BigDecimal.ZERO;
        BigDecimal da = BigDecimal.ZERO;
        for (SalaryComponentItemResponse item : earnings) {
            if (!item.enabled() || item.monthlyAmount() == null) {
                continue;
            }
            if (EarningKinds.isBasic(item.componentCode(), item.componentName())) {
                basic = basic.add(item.monthlyAmount());
            } else if (isDearnessAllowance(item.componentCode(), item.componentName())) {
                da = da.add(item.monthlyAmount());
            }
        }

        GratuityCalculator.Result result = GratuityCalculator.calculate(joiningDate, exitDate, reason, basic, da);
        return new ComputedGratuity(employee, result);
    }

    private static void validateInputs(UUID employeeId, LocalDate exitDate, GratuityReason reason) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (employeeId == null) {
            errors.put("employeeId", "must not be null");
        }
        if (exitDate == null) {
            errors.put("exitDate", "must not be null");
        }
        if (reason == null) {
            errors.put("reason", "must not be null");
        }
        if (!errors.isEmpty()) {
            throw new GratuityValidationException("Invalid gratuity request", errors);
        }
    }

    private static boolean isDearnessAllowance(String code, String name) {
        return matchesDa(code) || matchesDa(name);
    }

    private static boolean matchesDa(String value) {
        return value != null && DA_CODES.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private static GratuitySettlementResponse toResponse(GratuitySettlement entity) {
        return new GratuitySettlementResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                entity.getJoiningDate(),
                entity.getExitDate(),
                entity.getCompletedYears(),
                entity.isExemptFrom5YrRule(),
                entity.getLastDrawnBasic(),
                entity.getLastDrawnDa(),
                entity.getCalculatedAmount(),
                entity.getStatutoryCeiling(),
                entity.getGrossGratuityPayable(),
                entity.getTaxExemptAmount(),
                entity.getTaxableAmount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() <= MAX_ACTOR_LEN ? name : name.substring(0, MAX_ACTOR_LEN);
    }

    private record ComputedGratuity(EmployeeResponse employee, GratuityCalculator.Result result) {}
}
