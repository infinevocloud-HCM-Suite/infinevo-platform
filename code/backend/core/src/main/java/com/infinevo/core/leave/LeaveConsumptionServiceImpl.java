package com.infinevo.core.leave;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link LeaveConsumptionService} (W-16.4a).
 */
@Service
public class LeaveConsumptionServiceImpl implements LeaveConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(LeaveConsumptionServiceImpl.class);

    private final LeaveConsumptionRepository leaveConsumptionRepository;
    private final LeaveMonthlyLopRepository leaveMonthlyLopRepository;
    private final LeaveAllocationRepository leaveAllocationRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LopDerivationService lopDerivationService;
    private final PayInputService payInputService;

    public LeaveConsumptionServiceImpl(
            LeaveConsumptionRepository leaveConsumptionRepository,
            LeaveMonthlyLopRepository leaveMonthlyLopRepository,
            LeaveAllocationRepository leaveAllocationRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveBalanceService leaveBalanceService,
            LopDerivationService lopDerivationService,
            PayInputService payInputService) {
        this.leaveConsumptionRepository =
                Objects.requireNonNull(leaveConsumptionRepository, "leaveConsumptionRepository must not be null");
        this.leaveMonthlyLopRepository =
                Objects.requireNonNull(leaveMonthlyLopRepository, "leaveMonthlyLopRepository must not be null");
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.leaveBalanceService = Objects.requireNonNull(leaveBalanceService, "leaveBalanceService must not be null");
        this.lopDerivationService =
                Objects.requireNonNull(lopDerivationService, "lopDerivationService must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
    }

    @Override
    @Transactional
    public void consume(LeaveRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = request.getTenantId();
        UUID leaveRequestId = request.getId();

        // 1. Idempotency: duplicate consumption event writes nothing
        if (leaveRequestId != null
                && leaveConsumptionRepository.existsByTenantIdAndLeaveRequestIdAndReversesIdIsNull(
                        tenantId, leaveRequestId)) {
            log.info("Leave request {} already consumed for tenant {}, skipping duplicate", leaveRequestId, tenantId);
            return;
        }

        // 2. Fetch or resolve allocation
        LocalDate fromDate = request.getFromDate();
        LeaveAllocation allocation = leaveAllocationRepository
                .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                        tenantId, request.getEmployeeId(), request.getLeaveTypeId(), fromDate, fromDate)
                .or(() -> leaveAllocationRepository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndLeaveYear(
                        tenantId,
                        request.getEmployeeId(),
                        request.getLeaveTypeId(),
                        String.valueOf(fromDate.getYear())))
                .orElseThrow(() -> new IllegalStateException("No active leave allocation found for employee "
                        + request.getEmployeeId() + " and leave type " + request.getLeaveTypeId() + " on " + fromDate));

        // 3. Compute available balance BEFORE recording consumption
        BigDecimal availableBalance = leaveBalanceService
                .getBalance(tenantId, request.getEmployeeId(), request.getLeaveTypeId(), fromDate)
                .map(LeaveBalanceResponse::remainingDays)
                .orElse(BigDecimal.ZERO);

        // 4. Save LeaveConsumption record
        BigDecimal consumedDays = request.getWorkingDays().setScale(2, RoundingMode.HALF_UP);
        LocalDate consumedOn = LocalDate.now(ZoneOffset.UTC);
        String period = YearMonth.from(fromDate).toString();

        LeaveConsumption consumption = new LeaveConsumption(
                tenantId,
                request.getEmployeeId(),
                allocation.getId(),
                leaveRequestId,
                consumedDays,
                consumedOn,
                period,
                null,
                request.getReason());
        leaveConsumptionRepository.save(consumption);

        // 5. Derive loss-of-pay if policy mode is markAsLOP
        Optional<LeavePolicy> policyOpt =
                leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, request.getLeaveTypeId(), fromDate);

        if (policyOpt.isPresent() && policyOpt.get().getExceedBalanceMode() == ExceedBalanceMode.MARK_AS_LOP) {
            BigDecimal excessDays = BigDecimal.ZERO;
            if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
                excessDays = consumedDays;
            } else if (consumedDays.compareTo(availableBalance) > 0) {
                excessDays = consumedDays.subtract(availableBalance);
            }

            if (excessDays.compareTo(BigDecimal.ZERO) > 0) {
                Map<YearMonth, BigDecimal> monthSplit = lopDerivationService.splitExcessDays(
                        request.getFromDate(),
                        request.getToDate(),
                        request.isHalfDay(),
                        ExceedBalanceMode.MARK_AS_LOP,
                        excessDays);

                for (Map.Entry<YearMonth, BigDecimal> entry : monthSplit.entrySet()) {
                    YearMonth ym = entry.getKey();
                    BigDecimal lopDays = entry.getValue().setScale(2, RoundingMode.HALF_UP);

                    LeaveMonthlyLop monthlyLop = new LeaveMonthlyLop(
                            tenantId,
                            request.getEmployeeId(),
                            ym.toString(),
                            request.getLeaveTypeId(),
                            leaveRequestId,
                            lopDays,
                            null,
                            null);
                    monthlyLop = leaveMonthlyLopRepository.save(monthlyLop);

                    // Post to PayInputService (W-19)
                    PayInputCommand cmd = new PayInputCommand(
                            request.getEmployeeId(),
                            ym,
                            PayInputKind.LOP_DAYS,
                            lopDays,
                            null,
                            "core",
                            monthlyLop.getId().toString(),
                            null);
                    PayInputResponse payInputResp = payInputService.record(cmd);
                    monthlyLop.setPayInputId(payInputResp.id());
                    leaveMonthlyLopRepository.save(monthlyLop);
                }
            }
        }
    }

    @Override
    @Transactional
    public void cancel(LeaveRequest request, String reason) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = request.getTenantId();
        UUID leaveRequestId = request.getId();

        // 1. Reversing consumption rows
        List<LeaveConsumption> originalConsumptions =
                leaveConsumptionRepository.findByTenantIdAndLeaveRequestId(tenantId, leaveRequestId);
        for (LeaveConsumption orig : originalConsumptions) {
            if (orig.getReversesId() == null) {
                LeaveConsumption reversal = new LeaveConsumption(
                        tenantId,
                        orig.getEmployeeId(),
                        orig.getAllocationId(),
                        leaveRequestId,
                        orig.getConsumedDays().negate().setScale(2, RoundingMode.HALF_UP),
                        LocalDate.now(ZoneOffset.UTC),
                        orig.getPeriod(),
                        orig.getId(),
                        reason != null ? "Cancellation: " + reason : "Cancelled");
                leaveConsumptionRepository.save(reversal);
            }
        }

        // 2. Reversing monthly LOP rows and reversing pay inputs
        List<LeaveMonthlyLop> originalLops =
                leaveMonthlyLopRepository.findByTenantIdAndLeaveRequestIdAndReversesIdIsNull(tenantId, leaveRequestId);
        for (LeaveMonthlyLop origLop : originalLops) {
            UUID reversedPayInputId = null;
            if (origLop.getPayInputId() != null) {
                PayInputResponse reversed = payInputService.reverse(origLop.getPayInputId(), reason);
                reversedPayInputId = reversed.id();
            }

            LeaveMonthlyLop reversalLop = new LeaveMonthlyLop(
                    tenantId,
                    origLop.getEmployeeId(),
                    origLop.getPeriod(),
                    origLop.getLeaveTypeId(),
                    leaveRequestId,
                    origLop.getLopDays().negate().setScale(2, RoundingMode.HALF_UP),
                    origLop.getId(),
                    reversedPayInputId);
            leaveMonthlyLopRepository.save(reversalLop);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveConsumptionResponse> getConsumption(UUID employeeId, Integer year) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(employeeId, "employeeId must not be null");

        List<LeaveConsumption> list;
        if (year != null) {
            list =
                    leaveConsumptionRepository
                            .findByTenantIdAndEmployeeIdAndPeriodStartingWithOrderByConsumedOnAscCreatedAtAsc(
                                    tenantId, employeeId, String.valueOf(year));
        } else {
            list = leaveConsumptionRepository.findByTenantIdAndEmployeeIdOrderByConsumedOnAscCreatedAtAsc(
                    tenantId, employeeId);
        }
        return list.stream().map(LeaveConsumptionResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LopResponse getLop(UUID employeeId, YearMonth period) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(period, "period must not be null");

        String periodStr = period.toString();
        List<LeaveMonthlyLop> rows = leaveMonthlyLopRepository.findByTenantIdAndEmployeeIdAndPeriodOrderByCreatedAtAsc(
                tenantId, employeeId, periodStr);

        BigDecimal total = leaveMonthlyLopRepository.sumLopDaysByEmployeeAndPeriod(tenantId, employeeId, periodStr);

        List<LopResponse.LopDeltaItem> items =
                rows.stream().map(LopResponse.LopDeltaItem::from).toList();

        return new LopResponse(employeeId, periodStr, total.setScale(2, RoundingMode.HALF_UP), items);
    }
}
