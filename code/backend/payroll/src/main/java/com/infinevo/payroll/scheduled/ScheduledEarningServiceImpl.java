package com.infinevo.payroll.scheduled;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputRepository;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-73.6. A schedule is validated against the component catalogue and the employee before it is
 * written; {@link #materialise} is the one place an instalment becomes money, and it goes through
 * {@link PayInputService#record} — never a direct insert into the ledger — so the period lock, the
 * sign rule and the idempotency index all apply as they do to any other module's input.
 *
 * <p>Idempotency (§9): the ledger row's {@code source_ref} is {@code scheduled_earning:<id>:<period>},
 * unique per tenant with {@code uk_pay_input_tenant_source} ({@code V031}). Before writing, the
 * rows already present for the due instalments are read in one query; one that exists is counted
 * and not written again. That also repairs a schedule whose ledger row was written but whose counter
 * was not advanced — the two happen in one transaction, so it should not occur, but a retry is then
 * harmless rather than a double payment.
 */
@Service
public class ScheduledEarningServiceImpl implements ScheduledEarningService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEarningServiceImpl.class);

    /** How this module names itself on the ledger, as W-35.1's claims and W-35.2's deductions do. */
    static final String SOURCE_MODULE = "payroll";

    static final String TERMINATED_REASON = "terminated";

    static final int MIN_INSTALMENTS = 1;
    static final int MAX_INSTALMENTS = 12;
    private static final int MAX_REASON = 255;
    static final int MAX_AMOUNT_SCALE = 2;
    private static final int MAX_ACTOR = 100;

    private final ScheduledEarningRepository schedules;
    private final EarningRepository earnings;
    private final EmployeeService employeeService;
    private final PayInputService payInputService;
    private final PayInputRepository payInputs;
    private final Clock clock;

    /** One transaction per instalment in {@link #materialise}: a bad row rolls back alone (W-73.6 F-3). */
    private final TransactionTemplate perRow;

    /** The candidate read: the tenant binding is transaction-local, so even a read needs one. */
    private final TransactionTemplate readOnly;

    @Autowired
    public ScheduledEarningServiceImpl(
            ScheduledEarningRepository schedules,
            EarningRepository earnings,
            EmployeeService employeeService,
            PayInputService payInputService,
            PayInputRepository payInputs,
            PlatformTransactionManager transactionManager) {
        this(schedules, earnings, employeeService, payInputService, payInputs, transactionManager, Clock.systemUTC());
    }

    ScheduledEarningServiceImpl(
            ScheduledEarningRepository schedules,
            EarningRepository earnings,
            EmployeeService employeeService,
            PayInputService payInputService,
            PayInputRepository payInputs,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.schedules = Objects.requireNonNull(schedules, "schedules must not be null");
        this.earnings = Objects.requireNonNull(earnings, "earnings must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.payInputs = Objects.requireNonNull(payInputs, "payInputs must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.perRow = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledEarningResponse> listForEmployee(UUID employeeId) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId); // 404 for an employee outside the tenant
        List<ScheduledEarning> rows =
                schedules.findByTenantIdAndEmployeeIdOrderByFirstPeriodDescCreatedAtDesc(tenantId, employeeId);
        return toResponses(tenantId, rows);
    }

    @Override
    @Transactional
    public ScheduledEarningResponse create(UUID employeeId, ScheduledEarningRequest request) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();

        EmployeeResponse employee = employeeService.get(employeeId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (employee.status() == EmploymentStatus.TERMINATED) {
            errors.put("employeeId", "A terminated employee cannot be scheduled an earning");
        }

        Earning component = null;
        if (request.componentId() == null) {
            errors.put("componentId", "componentId is required");
        } else {
            component = earnings.findByIdAndTenantIdAndDeletedFalse(request.componentId(), tenantId)
                    .orElse(null);
            if (component == null) {
                errors.put("componentId", "No earning component " + request.componentId() + " in this tenant");
            } else if (!component.isActive()) {
                errors.put("componentId", "Component " + component.getCode() + " is inactive");
            } else if (!component.isScheduledEarning()) {
                errors.put(
                        "componentId",
                        "Component " + component.getCode() + " is not flagged Scheduled in the component drawer");
            }
        }

        if (request.amount() == null) {
            errors.put("amount", "amount is required");
        } else if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("amount", "amount must be greater than zero");
        } else if (request.amount().stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            // F-4: the instalments add back to the amount to the paisa, so it may carry no finer part.
            errors.put("amount", "amount can have at most " + MAX_AMOUNT_SCALE + " decimal places");
        }

        YearMonth firstPeriod = null;
        if (request.firstPeriod() == null || request.firstPeriod().isBlank()) {
            errors.put("firstPeriod", "firstPeriod is required, as yyyy-MM");
        } else {
            try {
                firstPeriod = YearMonth.parse(request.firstPeriod().trim());
                YearMonth current = YearMonth.now(clock);
                if (firstPeriod.isBefore(current)) {
                    errors.put("firstPeriod", "firstPeriod must be " + current + " or later");
                }
            } catch (DateTimeParseException e) {
                errors.put("firstPeriod", "firstPeriod must be a month as yyyy-MM");
            }
        }

        int instalments = request.instalments() == null ? MIN_INSTALMENTS : request.instalments();
        if (instalments < MIN_INSTALMENTS || instalments > MAX_INSTALMENTS) {
            errors.put("instalments", "instalments must be between " + MIN_INSTALMENTS + " and " + MAX_INSTALMENTS);
        } else if (!errors.containsKey("amount")
                && request.amount() != null
                && !ScheduledEarningInstalments.isSplittable(request.amount(), instalments)) {
            // F-3: an instalment under one paisa is refused by the ledger, so it is refused here first.
            errors.put(
                    "amount",
                    "amount must be at least "
                            + ScheduledEarningInstalments.MIN_INSTALMENT.multiply(BigDecimal.valueOf(instalments))
                            + " to pay "
                            + instalments
                            + " instalment(s) of at least 0.01");
        }

        String reason = trimToNull(request.reason());
        if (reason != null && reason.length() > MAX_REASON) {
            errors.put("reason", "reason cannot exceed " + MAX_REASON + " characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        ScheduledEarning saved = schedules.save(new ScheduledEarning(
                tenantId,
                employeeId,
                component.getId(),
                request.amount(),
                firstPeriod,
                instalments,
                reason,
                currentActor()));
        schedules.flush();
        log.info(
                "Scheduled earning {} for employee {} in tenant {}: {} {} from {} over {} instalment(s)",
                saved.getId(),
                employeeId,
                tenantId,
                component.getCode(),
                saved.getAmount(),
                firstPeriod,
                instalments);
        return ScheduledEarningResponse.from(saved, component.getCode(), component.getName(), List.of());
    }

    @Override
    @Transactional
    public ScheduledEarningResponse pause(UUID id, String reason) {
        UUID tenantId = TenantContext.require();
        ScheduledEarning row = require(tenantId, id);
        if (row.getStatus() != ScheduledEarningStatus.SCHEDULED) {
            throw new IllegalTransitionException(id, row.getStatus(), "paused");
        }
        row.pause(clip(trimToNull(reason)), currentActor());
        return single(tenantId, schedules.save(row));
    }

    @Override
    @Transactional
    public ScheduledEarningResponse resume(UUID id) {
        UUID tenantId = TenantContext.require();
        ScheduledEarning row = require(tenantId, id);
        if (row.getStatus() != ScheduledEarningStatus.PAUSED) {
            throw new IllegalTransitionException(id, row.getStatus(), "resumed");
        }
        row.resume(currentActor());
        return single(tenantId, schedules.save(row));
    }

    @Override
    @Transactional
    public ScheduledEarningResponse cancel(UUID id, String reason) {
        UUID tenantId = TenantContext.require();
        ScheduledEarning row = require(tenantId, id);
        if (row.getStatus().isTerminal()) {
            throw new IllegalTransitionException(id, row.getStatus(), "cancelled");
        }
        row.cancel(clip(trimToNull(reason)), currentActor());
        return single(tenantId, schedules.save(row));
    }

    /**
     * Not {@code @Transactional}: each due row is paid in a transaction of its own
     * ({@code REQUIRES_NEW}), so a row the ledger refuses is logged and skipped while the others
     * commit - one bad schedule never rolls back the tenant's other instalments, nor blocks the pay
     * run that called this (W-73.6 F-3). An overdue instalment is paid into {@code period}, keyed on
     * it, one per row per period (F-1).
     */
    @Override
    public int materialise(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();
        List<UUID> due = readOnly.execute(status -> schedules.findCandidatesDue(tenantId, period.atDay(1)).stream()
                .filter(row -> row.isDueIn(period))
                .map(ScheduledEarning::getId)
                .toList());

        int written = 0;
        int failed = 0;
        for (UUID id : due) {
            try {
                Boolean wrote = perRow.execute(status -> payOne(tenantId, id, period));
                if (Boolean.TRUE.equals(wrote)) {
                    written++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error(
                        "Scheduled earning {} in tenant {}: instalment for {} not paid, skipped; the others go on",
                        id,
                        tenantId,
                        period,
                        e);
            }
        }
        if (!due.isEmpty()) {
            log.info(
                    "Materialised scheduled earnings for {} in tenant {}: {} due, {} written, {} failed",
                    period,
                    tenantId,
                    due.size(),
                    written,
                    failed);
        }
        return written;
    }

    /**
     * Pays one row's next instalment into {@code period}, inside the caller's per-row transaction.
     * Re-reads the row there, so a change since the candidate read is respected. Returns whether a
     * ledger row was written - {@code false} when it already existed and was only counted.
     */
    private boolean payOne(UUID tenantId, UUID id, YearMonth period) {
        ScheduledEarning row = schedules.findByTenantIdAndId(tenantId, id).orElse(null);
        if (row == null || !row.isDueIn(period)) {
            return false;
        }
        String sourceRef = ScheduledEarningInstalments.sourceRef(row.getId(), period);
        boolean alreadyThere =
                payInputs
                        .findByTenantIdAndSourceModuleAndSourceRefIn(tenantId, SOURCE_MODULE, List.of(sourceRef))
                        .stream()
                        .anyMatch(existing -> !existing.isReversal());
        boolean wrote = false;
        if (alreadyThere) {
            log.info(
                    "Scheduled earning {} already has a pay input for {}; counting it, not writing it again",
                    row.getId(),
                    period);
        } else {
            BigDecimal amount = ScheduledEarningInstalments.amountOf(
                    row.getAmount(), row.getInstalments(), row.getPaidInstalments());
            PayInputResponse posted = payInputService.record(new PayInputCommand(
                    row.getEmployeeId(),
                    period,
                    PayInputKind.ONE_TIME_PAYOUT,
                    null,
                    Money.of(amount),
                    SOURCE_MODULE,
                    sourceRef));
            wrote = true;
            if (!period.equals(posted.postedPeriod())) {
                log.warn(
                        "Scheduled earning {}: period {} is locked, instalment {} posted to {}",
                        row.getId(),
                        period,
                        row.getPaidInstalments() + 1,
                        posted.postedPeriod());
            }
        }
        row.markInstalmentPaid(period, ScheduledEarning.ACTOR_SYSTEM);
        schedules.saveAndFlush(row);
        return wrote;
    }

    @Override
    @Transactional
    public int cancelForTerminatedEmployee(UUID employeeId) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();
        List<ScheduledEarning> open = schedules.findByTenantIdAndEmployeeIdAndStatusIn(
                tenantId, employeeId, List.of(ScheduledEarningStatus.SCHEDULED, ScheduledEarningStatus.PAUSED));
        for (ScheduledEarning row : open) {
            row.cancel(TERMINATED_REASON, ScheduledEarning.ACTOR_SYSTEM);
        }
        schedules.saveAll(open);
        if (!open.isEmpty()) {
            log.info(
                    "Employee {} terminated in tenant {}: cancelled {} scheduled earning(s)",
                    employeeId,
                    tenantId,
                    open.size());
        }
        return open.size();
    }

    private ScheduledEarning require(UUID tenantId, UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return schedules.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new NotFoundException(id));
    }

    private ScheduledEarningResponse single(UUID tenantId, ScheduledEarning row) {
        return toResponses(tenantId, List.of(row)).get(0);
    }

    /** Builds the responses for a set of rows with one component read and one ledger read. */
    private List<ScheduledEarningResponse> toResponses(UUID tenantId, List<ScheduledEarning> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Earning> components =
                earnings
                        .findAllById(rows.stream()
                                .map(ScheduledEarning::getEarningId)
                                .distinct()
                                .toList())
                        .stream()
                        .filter(e -> tenantId.equals(e.getTenantId()))
                        .collect(Collectors.toMap(Earning::getId, e -> e));

        Map<UUID, List<UUID>> payInputsByRow = new HashMap<>();
        for (UUID employeeId :
                rows.stream().map(ScheduledEarning::getEmployeeId).distinct().toList()) {
            payInputs
                    .findByTenantIdAndEmployeeIdAndSourceModuleAndSourceRefStartingWith(
                            tenantId, employeeId, SOURCE_MODULE, ScheduledEarningInstalments.SOURCE_REF_PREFIX)
                    .stream()
                    .filter(input -> !input.isReversal())
                    .sorted((x, y) -> x.getPeriod().compareTo(y.getPeriod()))
                    .forEach(input -> {
                        UUID rowId = ScheduledEarningInstalments.scheduleIdOf(input.getSourceRef());
                        if (rowId != null) {
                            payInputsByRow
                                    .computeIfAbsent(rowId, k -> new ArrayList<>())
                                    .add(input.getId());
                        }
                    });
        }

        List<ScheduledEarningResponse> out = new ArrayList<>(rows.size());
        for (ScheduledEarning row : rows) {
            Earning component = components.get(row.getEarningId());
            out.add(ScheduledEarningResponse.from(
                    row,
                    component != null ? component.getCode() : null,
                    component != null ? component.getName() : null,
                    payInputsByRow.getOrDefault(row.getId(), List.of())));
        }
        return out;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clip(String value) {
        return value != null && value.length() > MAX_REASON ? value.substring(0, MAX_REASON) : value;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ScheduledEarning.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
