package com.infinevo.payroll.challan;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.challan.TdsChallanResponse.AllocationStatus;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TDS challans (W-89.1). A challan's tax + surcharge + cess is split across employees' TDS on the approved or paid
 * runs of its quarter; no employee is covered beyond the TDS deducted from them, and no challan beyond its deposit.
 * A filed challan is locked.
 */
@Service
@Transactional
public class TdsChallanServiceImpl implements TdsChallanService {

    private static final int MAX_ACTOR_LEN = 100;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** TDS is deducted when salary is paid, so only paid runs are allocated (as Form 16 since D-150). */
    private static final Set<PayRunStatus> CLOSED = Set.of(PayRunStatus.PAID);

    private final TdsChallanRepository challanRepository;
    private final TdsChallanAllocationRepository allocationRepository;
    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository lineRepository;
    private final EmployeeRepository employeeRepository;
    private final TdsReturnRepository returnRepository;

    public TdsChallanServiceImpl(
            TdsChallanRepository challanRepository,
            TdsChallanAllocationRepository allocationRepository,
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository lineRepository,
            EmployeeRepository employeeRepository,
            TdsReturnRepository returnRepository) {
        this.challanRepository = Objects.requireNonNull(challanRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.payRunRepository = Objects.requireNonNull(payRunRepository);
        this.employeePayRunRepository = Objects.requireNonNull(employeePayRunRepository);
        this.lineRepository = Objects.requireNonNull(lineRepository);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.returnRepository = Objects.requireNonNull(returnRepository);
    }

    @Override
    public TdsChallanResponse create(TdsChallanRequest request) {
        UUID tenantId = TenantContext.require();
        validateRequest(request);
        refuseFiledQuarter(tenantId, request.financialYear(), request.quarter());
        if (challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(
                tenantId, request.bsrCode(), request.challanSerialNo(), request.tenderDate())) {
            throw new TdsChallanValidationException(
                    "A challan with this BSR code, serial number and tender date already exists");
        }
        TdsChallan challan = new TdsChallan();
        challan.setTenantId(tenantId);
        challan.setCreatedAt(Instant.now());
        challan.setCreatedBy(currentActor());
        apply(challan, request);
        return toResponse(challanRepository.saveAndFlush(challan), BigDecimal.ZERO);
    }

    @Override
    public TdsChallanResponse update(UUID id, TdsChallanRequest request) {
        UUID tenantId = TenantContext.require();
        validateRequest(request);
        TdsChallan challan = lock(tenantId, id);
        refuseFiled(challan, "changed");
        refuseFiledQuarter(tenantId, request.financialYear(), request.quarter());
        if (challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDateAndIdNot(
                tenantId, request.bsrCode(), request.challanSerialNo(), request.tenderDate(), id)) {
            throw new TdsChallanValidationException(
                    "A challan with this BSR code, serial number and tender date already exists");
        }
        BigDecimal allocated = allocatedTotal(tenantId, id);
        boolean quarterChanged = !challan.getFinancialYear().equals(request.financialYear())
                || !challan.getQuarter().equals(request.quarter());
        if (quarterChanged && allocated.signum() > 0) {
            throw new TdsChallanValidationException(
                    "Remove this challan's allocations before moving it to another quarter");
        }
        apply(challan, request);
        if (allocated.compareTo(challan.allocatable()) > 0) {
            throw new TdsChallanValidationException("Tax, surcharge and cess cannot be less than the "
                    + allocated.toPlainString() + " already allocated to employees");
        }
        return toResponse(challanRepository.saveAndFlush(challan), allocated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TdsChallanResponse> findById(UUID id) {
        UUID tenantId = TenantContext.require();
        return challanRepository
                .findByTenantIdAndId(tenantId, id)
                .map(c -> toResponse(c, allocatedTotal(tenantId, c.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsChallanResponse> list(String financialYear, String quarter) {
        UUID tenantId = TenantContext.require();
        List<TdsChallan> challans = quarter != null && !quarter.isBlank()
                ? challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
                        tenantId, financialYear, quarter.trim().toUpperCase(java.util.Locale.ROOT))
                : challanRepository.findByTenantIdAndFinancialYearOrderByTenderDateAsc(tenantId, financialYear);
        if (challans.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> allocated =
                allocationRepository
                        .findByTenantIdAndChallanIdIn(
                                tenantId,
                                challans.stream().map(TdsChallan::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                TdsChallanAllocation::getChallanId, TdsChallanAllocation::total, BigDecimal::add));
        return challans.stream()
                .map(c -> toResponse(c, allocated.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        TdsChallan challan = lock(tenantId, id);
        refuseFiled(challan, "deleted");
        allocationRepository.deleteByTenantIdAndChallanId(tenantId, id);
        challanRepository.delete(challan);
    }

    @Override
    public TdsChallanResponse allocate(UUID challanId, ChallanAllocationRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null || request.payRunId() == null) {
            throw new TdsChallanValidationException("Choose the pay run to allocate from");
        }
        List<ChallanAllocationRequest.EmployeeAllocationItem> items =
                request.employeeAllocations() == null ? List.of() : request.employeeAllocations();
        if (items.isEmpty()) {
            throw new TdsChallanValidationException("Give at least one employee's amounts");
        }
        // Every challan of the quarter, locked in id order before anything else: every allocation takes its
        // locks in the same order, so two saves in one quarter (same employee, different challans) wait for
        // each other instead of deadlocking, and the "not covered by other challans" check holds.
        TdsChallan challan = lockWithQuarter(tenantId, challanId);
        refuseFiled(challan, "reallocated");

        PayRun run = payRunRepository
                .findByIdAndTenantId(request.payRunId(), tenantId)
                .orElseThrow(() -> new TdsChallanValidationException("Pay run not found"));
        if (!CLOSED.contains(run.getStatus())) {
            throw new TdsChallanValidationException(
                    "Only a paid pay run's TDS can be allocated: TDS is deducted when salary is paid. This run is "
                            + run.getStatus());
        }
        TdsQuarter challanQuarter = TdsQuarter.parse(challan.getFinancialYear(), challan.getQuarter());
        TdsQuarter runQuarter = TdsQuarter.of(run);
        if (!runQuarter.equals(challanQuarter)) {
            throw new TdsChallanValidationException("This pay run (" + run.getPeriod() + ", paid "
                    + TdsQuarter.deductionDate(run) + ") is in " + runQuarter.label() + "; the challan is for "
                    + challanQuarter.label());
        }

        Map<UUID, EmployeePayRun> included = employeePayRunRepository
                .findAllByTenantIdAndPayrunIdAndInclusionStatus(tenantId, run.getId(), InclusionStatus.INCLUDED)
                .stream()
                .collect(Collectors.toMap(EmployeePayRun::getEmployeeId, Function.identity(), (a, b) -> a));
        Map<UUID, BigDecimal> tdsByEmployeeRun = tdsByEmployeeRun(tenantId, List.of(run.getId()));

        List<TdsChallanAllocation> runAllocations =
                new ArrayList<>(allocationRepository.findByTenantIdAndPayRunIdIn(tenantId, List.of(run.getId())));
        Set<UUID> seen = new HashSet<>();
        for (ChallanAllocationRequest.EmployeeAllocationItem item : items) {
            if (item == null || item.employeeId() == null) {
                throw new TdsChallanValidationException("Every allocation needs an employee");
            }
            if (!seen.add(item.employeeId())) {
                throw new TdsChallanValidationException("An employee is listed twice");
            }
            EmployeePayRun epr = included.get(item.employeeId());
            if (epr == null) {
                throw new TdsChallanValidationException("Employee " + item.employeeId() + " is not in this pay run");
            }
            BigDecimal tax = amount(item.taxAmount(), "Tax");
            BigDecimal surcharge = amount(item.surchargeAmount(), "Surcharge");
            BigDecimal cess = amount(item.cessAmount(), "Cess");
            BigDecimal wanted = tax.add(surcharge).add(cess);

            BigDecimal elsewhere = runAllocations.stream()
                    .filter(a -> a.getEmployeeId().equals(item.employeeId()))
                    .filter(a -> !a.getChallanId().equals(challanId))
                    .map(TdsChallanAllocation::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deducted = tdsByEmployeeRun.getOrDefault(epr.getId(), BigDecimal.ZERO);
            if (elsewhere.add(wanted).compareTo(deducted) > 0) {
                throw new TdsChallanValidationException("Employee " + item.employeeId() + ": "
                        + wanted.toPlainString() + " is more than the "
                        + deducted.subtract(elsewhere).max(BigDecimal.ZERO).toPlainString()
                        + " of TDS on this run not covered by other challans");
            }

            Optional<TdsChallanAllocation> existing = runAllocations.stream()
                    .filter(a -> a.getChallanId().equals(challanId)
                            && a.getEmployeeId().equals(item.employeeId()))
                    .findFirst();
            if (wanted.signum() == 0) {
                existing.ifPresent(a -> {
                    allocationRepository.delete(a);
                    runAllocations.remove(a);
                });
                continue;
            }
            TdsChallanAllocation row = existing.orElseGet(() -> {
                TdsChallanAllocation a = new TdsChallanAllocation();
                a.setTenantId(tenantId);
                a.setChallanId(challanId);
                a.setPayRunId(run.getId());
                a.setEmployeeId(item.employeeId());
                a.setCreatedBy(currentActor());
                runAllocations.add(a);
                return a;
            });
            row.setAllocatedTax(tax);
            row.setAllocatedSurcharge(surcharge);
            row.setAllocatedCess(cess);
            row.setUpdatedAt(Instant.now());
            row.setUpdatedBy(currentActor());
            allocationRepository.save(row);
        }
        allocationRepository.flush();

        BigDecimal allocated = allocatedTotal(tenantId, challanId);
        if (allocated.compareTo(challan.allocatable()) > 0) {
            throw new TdsChallanValidationException("These amounts would allocate " + allocated.toPlainString()
                    + ", more than the challan's " + challan.allocatable().toPlainString()
                    + " of tax, surcharge and cess");
        }
        return toResponse(challan, allocated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsChallanAllocationResponse> listAllocations(UUID challanId) {
        UUID tenantId = TenantContext.require();
        requireChallan(tenantId, challanId);
        List<TdsChallanAllocation> rows = allocationRepository.findByTenantIdAndChallanId(tenantId, challanId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Employee> employees = employees(
                tenantId, rows.stream().map(TdsChallanAllocation::getEmployeeId).collect(Collectors.toSet()));
        Map<UUID, String> periods = new HashMap<>();
        for (UUID runId : rows.stream().map(TdsChallanAllocation::getPayRunId).collect(Collectors.toSet())) {
            payRunRepository
                    .findByIdAndTenantId(runId, tenantId)
                    .ifPresent(r -> periods.put(runId, r.getPeriod().toString()));
        }
        return rows.stream()
                .map(a -> {
                    Employee e = employees.get(a.getEmployeeId());
                    return new TdsChallanAllocationResponse(
                            a.getId(),
                            a.getChallanId(),
                            a.getPayRunId(),
                            periods.get(a.getPayRunId()),
                            a.getEmployeeId(),
                            e == null ? null : e.getEmployeeNumber(),
                            name(e),
                            a.getAllocatedTax(),
                            a.getAllocatedSurcharge(),
                            a.getAllocatedCess(),
                            a.total());
                })
                .sorted(Comparator.comparing(
                                TdsChallanAllocationResponse::period, Comparator.nullsLast(String::compareTo))
                        .thenComparing(
                                TdsChallanAllocationResponse::employeeNumber, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationCandidate> candidates(UUID challanId) {
        UUID tenantId = TenantContext.require();
        TdsChallan challan = requireChallan(tenantId, challanId);
        TdsQuarter quarter = TdsQuarter.parse(challan.getFinancialYear(), challan.getQuarter());
        List<PayRun> runs = payRunRepository
                .findByTenantIdAndPeriodInAndStatusIn(
                        tenantId,
                        quarter.candidateMonths().stream()
                                .map(YearMonth::toString)
                                .toList(),
                        List.copyOf(CLOSED))
                .stream()
                .filter(r -> TdsQuarter.of(r).equals(quarter))
                .toList();
        if (runs.isEmpty()) {
            return List.of();
        }
        List<UUID> runIds = runs.stream().map(PayRun::getId).toList();
        Map<UUID, PayRun> runById = runs.stream().collect(Collectors.toMap(PayRun::getId, Function.identity()));
        List<EmployeePayRun> eprs = employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                tenantId, runIds, InclusionStatus.INCLUDED);
        Map<UUID, BigDecimal> tds = tdsByEmployeeRun(tenantId, runIds);
        List<TdsChallanAllocation> allocations = allocationRepository.findByTenantIdAndPayRunIdIn(tenantId, runIds);
        Map<UUID, Employee> employees = employees(
                tenantId, eprs.stream().map(EmployeePayRun::getEmployeeId).collect(Collectors.toSet()));

        List<AllocationCandidate> out = new ArrayList<>();
        for (EmployeePayRun epr : eprs) {
            BigDecimal deducted = tds.getOrDefault(epr.getId(), BigDecimal.ZERO);
            if (deducted.signum() <= 0) {
                continue;
            }
            BigDecimal here = BigDecimal.ZERO;
            BigDecimal elsewhere = BigDecimal.ZERO;
            for (TdsChallanAllocation a : allocations) {
                if (a.getPayRunId().equals(epr.getPayrunId())
                        && a.getEmployeeId().equals(epr.getEmployeeId())) {
                    if (a.getChallanId().equals(challanId)) {
                        here = here.add(a.total());
                    } else {
                        elsewhere = elsewhere.add(a.total());
                    }
                }
            }
            PayRun run = runById.get(epr.getPayrunId());
            Employee e = employees.get(epr.getEmployeeId());
            out.add(new AllocationCandidate(
                    run.getId(),
                    run.getPeriod().toString(),
                    run.getRunType() == null ? null : run.getRunType().name(),
                    epr.getEmployeeId(),
                    e == null ? null : e.getEmployeeNumber(),
                    name(e),
                    deducted,
                    elsewhere,
                    here,
                    deducted.subtract(elsewhere).subtract(here).max(BigDecimal.ZERO)));
        }
        out.sort(Comparator.comparing(AllocationCandidate::period)
                .thenComparing(AllocationCandidate::employeeNumber, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    @Override
    public TdsReturnResponse markFiled(String financialYear, String quarter, TdsReturnRequest request) {
        UUID tenantId = TenantContext.require();
        TdsQuarter q = quarter(financialYear, quarter);
        if (request == null
                || request.tokenNumber() == null
                || !request.tokenNumber().trim().matches("^[0-9]{15}$")) {
            throw new TdsChallanValidationException("The token number is the 15 digits on the filing acknowledgement");
        }
        LocalDate today = LocalDate.now(IST);
        LocalDate filedOn = request.filedOn() == null ? today : request.filedOn();
        if (filedOn.isAfter(today)) {
            throw new TdsChallanValidationException("The filing date cannot be in the future");
        }
        // Waits for any allocation save in this quarter to finish, so none commits after the quarter is filed.
        challanRepository.lockQuarter(tenantId, q.financialYear().label(), q.quarter());
        if (returnRepository.existsByTenantIdAndFinancialYearAndQuarter(
                tenantId, q.financialYear().label(), q.quarter())) {
            throw new TdsChallanValidationException(q.label() + " is already marked filed");
        }
        TdsReturn saved = returnRepository.saveAndFlush(
                new TdsReturn(tenantId, q, request.tokenNumber().trim(), filedOn, currentActor()));
        return TdsReturnResponse.from(saved);
    }

    @Override
    public void reopen(String financialYear, String quarter) {
        UUID tenantId = TenantContext.require();
        TdsQuarter q = quarter(financialYear, quarter);
        returnRepository
                .findByTenantIdAndFinancialYearAndQuarter(
                        tenantId, q.financialYear().label(), q.quarter())
                .ifPresent(returnRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsReturnResponse> returns(String financialYear) {
        UUID tenantId = TenantContext.require();
        String fy = quarter(financialYear, "Q1").financialYear().label();
        return returnRepository.findByTenantIdAndFinancialYearOrderByQuarterAsc(tenantId, fy).stream()
                .map(TdsReturnResponse::from)
                .toList();
    }

    private static TdsQuarter quarter(String financialYear, String quarter) {
        try {
            return TdsQuarter.parse(financialYear, quarter);
        } catch (IllegalArgumentException e) {
            throw new TdsChallanValidationException(e.getMessage());
        }
    }

    private Map<UUID, BigDecimal> tdsByEmployeeRun(UUID tenantId, List<UUID> runIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (EmployeePayRunLine line : lineRepository.findTaxLinesByPayrunIds(tenantId, runIds)) {
            if (line.getAmount() != null) {
                out.merge(line.getEmployeePayrunId(), line.getAmount(), BigDecimal::add);
            }
        }
        return out;
    }

    private Map<UUID, Employee> employees(UUID tenantId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private static String name(Employee e) {
        if (e == null) {
            return null;
        }
        String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
        String last = e.getLastName() == null ? "" : e.getLastName().trim();
        return (first + " " + last).trim();
    }

    private TdsChallan lock(UUID tenantId, UUID id) {
        return challanRepository
                .lockByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS challan not found: " + id));
    }

    /** Locks every challan of this challan's quarter in id order, then returns this one. */
    private TdsChallan lockWithQuarter(UUID tenantId, UUID id) {
        TdsChallanRepository.QuarterKey key = challanRepository
                .findQuarterKey(tenantId, id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS challan not found: " + id));
        return challanRepository.lockQuarter(tenantId, key.getFinancialYear(), key.getQuarter()).stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new TdsChallanValidationException(
                        "This challan was changed by someone else just now; open it again and retry"));
    }

    private TdsChallan requireChallan(UUID tenantId, UUID id) {
        return challanRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS challan not found: " + id));
    }

    private void refuseFiled(TdsChallan challan, String verb) {
        if (isFiled(challan)) {
            throw new TdsChallanValidationException("The " + challan.getFinancialYear() + " " + challan.getQuarter()
                    + " return is marked filed, so this challan cannot be " + verb + "; reopen the quarter first");
        }
    }

    private void refuseFiledQuarter(UUID tenantId, String financialYear, String quarter) {
        if (returnRepository.existsByTenantIdAndFinancialYearAndQuarter(tenantId, financialYear, quarter)) {
            throw new TdsChallanValidationException(
                    "The " + financialYear + " " + quarter + " return is marked filed; reopen the quarter first");
        }
    }

    private boolean isFiled(TdsChallan challan) {
        return returnRepository.existsByTenantIdAndFinancialYearAndQuarter(
                challan.getTenantId(), challan.getFinancialYear(), challan.getQuarter());
    }

    private BigDecimal allocatedTotal(UUID tenantId, UUID challanId) {
        return allocationRepository.findByTenantIdAndChallanId(tenantId, challanId).stream()
                .map(TdsChallanAllocation::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void apply(TdsChallan challan, TdsChallanRequest request) {
        challan.setFinancialYear(request.financialYear());
        challan.setQuarter(request.quarter());
        challan.setBsrCode(request.bsrCode());
        challan.setChallanSerialNo(request.challanSerialNo());
        challan.setTenderDate(request.tenderDate());
        // Challans are paid in whole rupees, and the Form 138 file only takes whole-rupee challan amounts.
        challan.setTaxAmount(rupees(request.taxAmount(), "Tax"));
        challan.setSurchargeAmount(rupees(request.surchargeAmount(), "Surcharge"));
        challan.setCessAmount(rupees(request.cessAmount(), "Cess"));
        challan.setInterestAmount(rupees(request.interestAmount(), "Interest"));
        challan.setFeePenaltyAmount(rupees(request.feePenaltyAmount(), "Fee"));
        challan.setTotalAmount(
                challan.allocatable().add(challan.getInterestAmount()).add(challan.getFeePenaltyAmount()));
        challan.setMinorHead(request.minorHead() != null ? request.minorHead() : "200");
        challan.setRemarks(request.remarks());
        touch(challan);
    }

    private static void touch(TdsChallan challan) {
        challan.setUpdatedAt(Instant.now());
        challan.setUpdatedBy(currentActor());
    }

    /** A challan amount: as {@link #amount} and whole rupees. */
    static BigDecimal rupees(BigDecimal value, String label) {
        BigDecimal v = amount(value, label);
        if (v.stripTrailingZeros().scale() > 0) {
            throw new TdsChallanValidationException(label + " on a challan is in whole rupees");
        }
        return v;
    }

    /** A money input: null is zero; negative or more than two decimals is refused. */
    static BigDecimal amount(BigDecimal value, String label) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value.signum() < 0) {
            throw new TdsChallanValidationException(label + " cannot be negative");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new TdsChallanValidationException(label + " can have at most two decimals");
        }
        if (value.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new TdsChallanValidationException(label + " is too large");
        }
        return value.setScale(2);
    }

    private void validateRequest(TdsChallanRequest req) {
        if (req == null) {
            throw new TdsChallanValidationException("Request cannot be null");
        }
        try {
            TdsQuarter.parse(req.financialYear(), req.quarter());
        } catch (IllegalArgumentException e) {
            throw new TdsChallanValidationException(e.getMessage());
        }
        if (!req.quarter().equals(req.quarter().trim().toUpperCase(java.util.Locale.ROOT))
                || !req.financialYear().equals(req.financialYear().trim())) {
            throw new TdsChallanValidationException("Financial year must look like 2026-2027 and quarter like Q1");
        }
        if (req.bsrCode() == null || !req.bsrCode().matches("^[0-9]{7}$")) {
            throw new TdsChallanValidationException("BSR code must be exactly 7 digits");
        }
        if (req.challanSerialNo() == null || !req.challanSerialNo().matches("^[0-9]{1,5}$")) {
            throw new TdsChallanValidationException("Challan serial number must be 1 to 5 digits");
        }
        if (req.tenderDate() == null) {
            throw new TdsChallanValidationException("Tender date is required");
        }
        LocalDate earliest = TdsQuarter.parse(req.financialYear(), req.quarter())
                .financialYear()
                .start()
                .minusYears(1);
        if (req.tenderDate().isBefore(earliest) || req.tenderDate().isAfter(LocalDate.now(IST))) {
            throw new TdsChallanValidationException("Tender date must be between " + earliest + " and today");
        }
        if (req.minorHead() != null && !List.of("200", "400").contains(req.minorHead())) {
            throw new TdsChallanValidationException("Minor head must be 200 or 400");
        }
        if (req.remarks() != null && req.remarks().length() > 255) {
            throw new TdsChallanValidationException("Remarks can be at most 255 characters");
        }
    }

    private TdsChallanResponse toResponse(TdsChallan c, BigDecimal allocated) {
        BigDecimal allocatable = c.allocatable();
        BigDecimal unallocated = allocatable.subtract(allocated);
        AllocationStatus status = allocated.signum() == 0
                ? (allocatable.signum() == 0 ? AllocationStatus.FULL : AllocationStatus.UNALLOCATED)
                : unallocated.signum() <= 0 ? AllocationStatus.FULL : AllocationStatus.PARTIAL;
        return new TdsChallanResponse(
                c.getId(),
                c.getFinancialYear(),
                c.getQuarter(),
                c.getBsrCode(),
                c.getChallanSerialNo(),
                c.getTenderDate(),
                c.getTaxAmount(),
                c.getSurchargeAmount(),
                c.getCessAmount(),
                c.getInterestAmount(),
                c.getFeePenaltyAmount(),
                c.getTotalAmount(),
                allocatable,
                allocated,
                unallocated,
                status,
                c.getMinorHead(),
                isFiled(c),
                c.getRemarks(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
    }
}
