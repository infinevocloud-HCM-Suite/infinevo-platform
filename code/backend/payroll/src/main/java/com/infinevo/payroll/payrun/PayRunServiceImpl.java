package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodResponse;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-29.1. {@link #create} is not {@code @Transactional}: it gathers the period, the employees and the
 * inclusion decisions through their own services — each in its own transaction, so a "no salary"
 * throw from {@code versionInForce} cannot mark this one rollback-only — and then writes the run and
 * its rows in one transaction of its own. The unique index {@code uk_payrun_tenant_period} decides a
 * race between two creates; the {@code exists} read before it only saves the work of a certain loser.
 *
 * <p>{@link #compute} does not compute (W-29.4 §3): it starts an attempt under the run's row lock — the
 * guard against two officers pressing compute at once — creates the job, and sends the message to the
 * {@code payrun} queue after the transaction commits, so the worker never reads a run still
 * {@code LOCKED}. The worker's {@code PayrunQueueListener} runs {@link PayRunComputationService}.
 */
@Service
public class PayRunServiceImpl implements PayRunService {

    private static final Logger log = LoggerFactory.getLogger(PayRunServiceImpl.class);

    static final String ACTOR_SYSTEM = "system";

    /** The queue W-52 created for pay runs; the worker's {@code PayrunQueueListener} reads it. */
    public static final String QUEUE_NAME = "payrun";

    /** A {@code COMPUTING} run whose job has not moved for this long may be computed again (§3). */
    static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private static final int ACTOR_MAX_LENGTH = 100;
    private static final String UNIQUE_PERIOD_INDEX = "uk_payrun_tenant_period";

    /** How this module names itself on the pay input ledger, as W-35.1's claims do. */
    static final String SOURCE_MODULE = "payroll";

    private final PayRunRepository payRuns;
    private final EmployeePayRunRepository employeePayRuns;
    private final PayPeriodService payPeriodService;
    private final EmployeeService employeeService;
    private final PayRunInclusionService inclusionService;
    private final PayInputService payInputService;
    private final EmployeePayRunLineRepository lines;
    private final JobService jobService;
    private final ObjectProvider<QueueProducer> queueProducers;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public PayRunServiceImpl(
            PayRunRepository payRuns,
            EmployeePayRunRepository employeePayRuns,
            PayPeriodService payPeriodService,
            EmployeeService employeeService,
            PayRunInclusionService inclusionService,
            PayInputService payInputService,
            EmployeePayRunLineRepository lines,
            JobService jobService,
            ObjectProvider<QueueProducer> queueProducers,
            PlatformTransactionManager transactionManager) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.payPeriodService = Objects.requireNonNull(payPeriodService, "payPeriodService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.inclusionService = Objects.requireNonNull(inclusionService, "inclusionService must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.queueProducers = Objects.requireNonNull(queueProducers, "queueProducers must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public PayRunResponse create(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();

        // W-30.2: only a regular run blocks the month; off-cycle runs for it do not.
        if (Boolean.TRUE.equals(
                readTransaction.execute(status -> payRuns.existsByTenantIdAndPeriodAndRunTypeAndStatusNot(
                        tenantId, period.toString(), PayRunType.REGULAR, PayRunStatus.CANCELLED)))) {
            throw new DuplicatePayRunException(period);
        }

        PayPeriodResponse dates = payPeriodService.periodFor(period);
        List<EmployeeResponse> employees = employeeService.listEmployedBetween(dates.start(), dates.end());
        List<InclusionDecision> decisions = inclusionService.decide(tenantId, employees, dates.start(), dates.end());
        int included = (int) decisions.stream()
                .filter(d -> d.inclusionStatus() == InclusionStatus.INCLUDED)
                .count();
        int skipped = decisions.size() - included;
        String actor = currentActor();

        try {
            PayRun saved = writeTransaction.execute(status -> {
                PayRun run = payRuns.saveAndFlush(new PayRun(
                        tenantId,
                        period,
                        dates.start(),
                        dates.end(),
                        dates.cutoffDate(),
                        dates.payDate(),
                        included,
                        skipped,
                        actor));
                employeePayRuns.saveAll(decisions.stream()
                        .map(d -> new EmployeePayRun(tenantId, run.getId(), d, actor))
                        .toList());
                employeePayRuns.flush();
                return run;
            });
            log.info(
                    "Created pay run {} for {} in tenant {}: {} included, {} skipped",
                    saved.getId(),
                    period,
                    tenantId,
                    included,
                    skipped);
            return PayRunResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNIQUE_PERIOD_INDEX)) {
                throw new DuplicatePayRunException(period);
            }
            throw e;
        }
    }

    /**
     * W-30.2 §3. Not {@code @Transactional}, as {@link #create}: the seams run in their own
     * transactions, so a "no salary" throw cannot mark this one rollback-only. No uniqueness check —
     * a second off-cycle run for the month is fine.
     */
    @Override
    public PayRunResponse createOffCycle(LocalDate payDate, List<UUID> employeeIds, String notes) {
        if (payDate == null) {
            throw new IllegalArgumentException("pay_date is required, as YYYY-MM-DD");
        }
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new IllegalArgumentException("employee_ids must name at least one employee");
        }
        if (employeeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("employee_ids must not contain null");
        }
        if (notes != null && notes.strip().length() > 500) {
            throw new IllegalArgumentException("notes must be at most 500 characters");
        }
        UUID tenantId = TenantContext.require();
        YearMonth period = YearMonth.from(payDate);
        PayPeriodResponse dates = payPeriodService.periodFor(period);

        // Named twice is paid once: one row per employee, in the order first named.
        Set<UUID> named = new LinkedHashSet<>(employeeIds);
        List<EmployeeResponse> employees = new ArrayList<>(named.size());
        List<UUID> unknown = new ArrayList<>();
        for (UUID employeeId : named) {
            try {
                employees.add(employeeService.get(employeeId));
            } catch (EmployeeService.NotFoundException e) {
                unknown.add(employeeId);
            }
        }
        if (!unknown.isEmpty()) {
            throw new EmployeeNotInRunException("No such employee in this tenant", unknown);
        }
        List<OffCycleInclusion> inclusions = inclusionService.forNamed(tenantId, employees, dates.start(), dates.end());
        int included = (int) inclusions.stream()
                .filter(i -> i.inclusionStatus() == InclusionStatus.INCLUDED)
                .count();
        int skipped = inclusions.size() - included;
        String actor = currentActor();

        PayRun saved = Objects.requireNonNull(writeTransaction.execute(status -> {
            PayRun run = payRuns.saveAndFlush(PayRun.ofType(
                    PayRunType.OFF_CYCLE,
                    tenantId,
                    period,
                    dates.start(),
                    dates.end(),
                    dates.cutoffDate(),
                    payDate,
                    notes,
                    included,
                    skipped,
                    actor));
            employeePayRuns.saveAll(inclusions.stream()
                    .map(i -> new EmployeePayRun(tenantId, run.getId(), i, actor))
                    .toList());
            employeePayRuns.flush();
            return run;
        }));
        log.info(
                "Created off-cycle pay run {} for {} in tenant {}, paid {}: {} included, {} skipped",
                saved.getId(),
                period,
                tenantId,
                payDate,
                included,
                skipped);
        return PayRunResponse.from(saved);
    }

    /**
     * W-30.2 §3. Every item is checked first, so a bad item writes nothing. Then each goes through
     * {@code PayInputService.record} in its own transaction — not one around them all, where the first
     * duplicate would abort the rest — and a duplicate is reported for that item alone. A lock that
     * lands between the check and a write is the ledger's {@code RunLockedException}, a {@code 409}.
     */
    @Override
    public List<PayRunInputResponse> addInputs(UUID id, List<PayRunInputRequest> inputs) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        PayRun run = Objects.requireNonNull(readTransaction.execute(status -> require(id)));
        if (run.getRunType() != PayRunType.OFF_CYCLE || run.getStatus() != PayRunStatus.DRAFT) {
            throw new NotAnOffCycleRunException(id, run.getRunType(), run.getStatus());
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one input is required");
        }
        for (int i = 0; i < inputs.size(); i++) {
            validateInput(i, inputs.get(i));
        }
        Set<UUID> includedIds = Objects.requireNonNull(readTransaction.execute(status ->
                employeePayRuns
                        .findAllByTenantIdAndPayrunIdAndInclusionStatus(tenantId, id, InclusionStatus.INCLUDED)
                        .stream()
                        .map(EmployeePayRun::getEmployeeId)
                        .collect(Collectors.toSet())));
        List<UUID> notIncluded = inputs.stream()
                .map(PayRunInputRequest::employeeId)
                .filter(employeeId -> !includedIds.contains(employeeId))
                .distinct()
                .toList();
        if (!notIncluded.isEmpty()) {
            throw new EmployeeNotInRunException("Not an included employee of pay run " + id, notIncluded);
        }

        List<PayRunInputResponse> results = new ArrayList<>(inputs.size());
        for (PayRunInputRequest input : inputs) {
            String sourceRef = input.sourceRef().strip();
            PayInputCommand command = new PayInputCommand(
                    input.employeeId(),
                    run.getPeriod(),
                    input.kind(),
                    null,
                    Money.of(input.amount()),
                    SOURCE_MODULE,
                    "payrun:" + id + ":" + sourceRef,
                    id);
            try {
                PayInputResponse recorded = payInputService.record(command);
                results.add(PayRunInputResponse.recorded(input.employeeId(), sourceRef, recorded.id()));
            } catch (PayInputService.DuplicatePayInputException e) {
                results.add(PayRunInputResponse.duplicate(input.employeeId(), sourceRef));
            }
        }
        long recorded = results.stream()
                .filter(r -> r.result() == PayRunInputResponse.Result.RECORDED)
                .count();
        log.info(
                "Off-cycle pay run {} in tenant {}: {} inputs recorded, {} duplicates",
                id,
                tenantId,
                recorded,
                results.size() - recorded);
        return results;
    }

    private static void validateInput(int index, PayRunInputRequest input) {
        String at = "inputs[" + index + "]";
        if (input == null) {
            throw new IllegalArgumentException(at + " is null");
        }
        if (input.employeeId() == null) {
            throw new IllegalArgumentException(at + ".employee_id is required");
        }
        if (input.kind() == null) {
            throw new IllegalArgumentException(at + ".kind is required");
        }
        if (input.kind() == PayInputKind.LOP_DAYS) {
            throw new IllegalArgumentException(
                    at + ".kind LOP_DAYS is not allowed: an off-cycle run has no loss of pay");
        }
        if (input.amount() == null || input.amount().signum() <= 0) {
            throw new IllegalArgumentException(at + ".amount must be above zero");
        }
        if (input.sourceRef() == null || input.sourceRef().isBlank()) {
            throw new IllegalArgumentException(at + ".source_ref is required");
        }
        if (input.sourceRef().strip().length() > MAX_INPUT_SOURCE_REF_LENGTH) {
            throw new IllegalArgumentException(
                    at + ".source_ref must be at most " + MAX_INPUT_SOURCE_REF_LENGTH + " characters");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PayRunResponse get(UUID id) {
        return PayRunResponse.from(require(id));
    }

    /** Overrides the interface default so a call through the proxy is transactional too. */
    @Override
    @Transactional(readOnly = true)
    public Page<PayRunResponse> list(PayRunStatus status, Pageable pageable) {
        return list(status, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayRunResponse> list(PayRunStatus status, PayRunType runType, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<PayRun> page;
        if (runType == null) {
            page = status == null
                    ? payRuns.findByTenantId(tenantId, pageable)
                    : payRuns.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = status == null
                    ? payRuns.findByTenantIdAndRunType(tenantId, runType, pageable)
                    : payRuns.findByTenantIdAndStatusAndRunType(tenantId, status, runType, pageable);
        }
        return page.map(PayRunResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeePayRunResponse> employees(UUID id, InclusionStatus inclusion, Pageable pageable) {
        PayRun run = require(id);
        Page<EmployeePayRun> rows = inclusion == null
                ? employeePayRuns.findByTenantIdAndPayrunId(run.getTenantId(), run.getId(), pageable)
                : employeePayRuns.findByTenantIdAndPayrunIdAndInclusionStatus(
                        run.getTenantId(), run.getId(), inclusion, pageable);
        // One read for the page's numbers, through the core seam — the rows carry no snapshot.
        Map<UUID, String> numbers = new HashMap<>();
        for (EmployeeResponse employee :
                employeeService.listEmployedBetween(run.getPeriodStart(), run.getPeriodEnd())) {
            numbers.put(employee.id(), employee.employeeNumber());
        }
        return rows.map(row -> EmployeePayRunResponse.from(row, numbers.get(row.getEmployeeId())));
    }

    @Override
    @Transactional
    public PayRunResponse lock(UUID id) {
        PayRun run = requireForUpdate(id);
        // Refuse before touching the input lock, so a 409 leaves nothing behind.
        run.getStatus().requireTransitionTo(PayRunStatus.LOCKED);
        if (run.getRunType() == PayRunType.OFF_CYCLE) {
            // W-30.2: the run's own inputs, never the month — the regular run still locks its period.
            payInputService.lockRun(run.getId(), run.getPeriod());
        } else {
            payInputService.lock(run.getPeriod());
        }
        run.lock(currentActor(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        PayRun saved = payRuns.saveAndFlush(run);
        log.info("Locked pay run {} for {} in tenant {}", saved.getId(), saved.getPeriod(), saved.getTenantId());
        return PayRunResponse.from(saved);
    }

    @Override
    @Transactional
    public PayRunResponse cancel(UUID id) {
        PayRun run = requireForUpdate(id);
        run.cancel(currentActor(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        PayRun saved = payRuns.saveAndFlush(run);
        log.info("Cancelled pay run {} for {} in tenant {}", saved.getId(), saved.getPeriod(), saved.getTenantId());
        return PayRunResponse.from(saved);
    }

    /**
     * Not {@code @Transactional}: the attempt is started and committed in one transaction, and the
     * message sent only after it — a message the worker cannot act on yet is a retry we do not need
     * (W-29.4 §13 decision 5).
     */
    @Override
    public ComputeAcceptedResponse compute(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        String actor = currentActor();
        QueueProducer producer = queueProducers.getIfAvailable();

        PayRun started = Objects.requireNonNull(writeTransaction.execute(status -> {
            PayRun run = payRuns.findForUpdate(id, tenantId).orElseThrow(() -> new PayRunNotFoundException(id));
            boolean resuming = run.getStatus() == PayRunStatus.COMPUTING;
            if (resuming) {
                if (!isAbandoned(run, tenantId)) {
                    throw new PayRunComputeInProgressException(id, STALE_AFTER.toMinutes());
                }
                run.resumeComputing(actor);
            } else {
                run.startComputing(actor);
            }
            // Checked after the status, so a 404 or a 409 still says what is wrong with the run.
            if (producer == null) {
                throw new PayRunEnqueueException(id, "no queue is configured", null);
            }
            // Only a resumed attempt keeps rows; one from LOCKED, COMPUTED or FAILED recomputes
            // everyone. The carry clears the persistence context, so the run is read again after it.
            int carried = 0;
            if (resuming && run.getComputeAttempt() > 0) {
                int previous = run.getComputeAttempt();
                payRuns.saveAndFlush(run);
                carried = employeePayRuns.carryForward(tenantId, id, previous, previous + 1);
                run = payRuns.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new PayRunNotFoundException(id));
            }
            int attempt = run.beginAttempt(QUEUE_NAME + "-" + id + "-", carried, now());
            PayRun saved = payRuns.saveAndFlush(run);
            jobService.createJob(
                    saved.getJobId(), tenantId, QUEUE_NAME, new PayRunJobPayload(id, attempt, actor).toJson());
            return saved;
        }));

        int attempt = started.getComputeAttempt();
        String jobId = started.getJobId();
        try {
            producer.send(
                    QUEUE_NAME,
                    QueueMessage.of(jobId, tenantId, QUEUE_NAME, new PayRunJobPayload(id, attempt, actor).toJson()));
        } catch (RuntimeException e) {
            log.error("Pay run {} attempt {} could not be queued as job {}", id, attempt, jobId, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            PayRunEnqueueException failure = new PayRunEnqueueException(id, reason, e);
            try {
                abandonAttempt(id, tenantId, attempt, jobId, actor, failure.getMessage());
            } catch (RuntimeException secondary) {
                failure.addSuppressed(secondary);
            }
            throw failure;
        }
        log.info(
                "Queued pay run {} for {} in tenant {}: attempt {}, job {}, {} of {} employees carried forward",
                id,
                started.getPeriod(),
                tenantId,
                attempt,
                jobId,
                started.getProgressDone(),
                started.getProgressTotal());
        return new ComputeAcceptedResponse(jobId, PayRunStatus.COMPUTING, attempt);
    }

    /**
     * The stale rule (W-29.4 §3): the run's job is gone; or finished — {@code FAILED} after its last
     * delivery, so no worker will ever finish the run; or neither the job nor the run has been touched
     * for {@link #STALE_AFTER}. Every progress report writes the run's counter; the job's percentage
     * only moves when it changes, which on a large run is not every report.
     */
    private boolean isAbandoned(PayRun run, UUID tenantId) {
        if (run.getJobId() == null) {
            return true;
        }
        Optional<JobStatusResponseDTO> job = jobService.getJobStatus(run.getJobId(), tenantId);
        if (job.isEmpty() || job.get().updatedAt() == null) {
            return true;
        }
        if (job.get().status() == JobState.COMPLETED || job.get().status() == JobState.FAILED) {
            return true;
        }
        Instant lastTouched = job.get().updatedAt();
        if (run.getUpdatedAt() != null && run.getUpdatedAt().isAfter(lastTouched)) {
            lastTouched = run.getUpdatedAt();
        }
        return lastTouched.isBefore(Instant.now().minus(STALE_AFTER));
    }

    /**
     * The message never left: the run ends {@code FAILED} with the reason and the job {@code FAILED},
     * so the officer can compute again at once instead of waiting out the stale window.
     */
    private void abandonAttempt(UUID id, UUID tenantId, int attempt, String jobId, String actor, String reason) {
        writeTransaction.executeWithoutResult(status -> {
            PayRun run = payRuns.findForUpdate(id, tenantId).orElseThrow(() -> new PayRunNotFoundException(id));
            if (run.getStatus() == PayRunStatus.COMPUTING && run.getComputeAttempt() == attempt) {
                run.failComputation(
                        reason,
                        run.getTotalGross(),
                        run.getTotalDeductions(),
                        run.getTotalNetPay(),
                        run.getNegativeNetCount(),
                        actor,
                        now());
                payRuns.saveAndFlush(run);
            }
            jobService.markFailed(jobId, reason);
        });
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeePayRunLinesResponse lines(UUID id, UUID employeeId) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        PayRun run = require(id);
        EmployeePayRun row = employeePayRuns
                .findByTenantIdAndPayrunIdAndEmployeeId(run.getTenantId(), run.getId(), employeeId)
                .orElseThrow(() -> new PayRunNotFoundException(id));
        List<EmployeePayRunLineResponse> rowLines =
                lines.findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(run.getTenantId(), row.getId()).stream()
                        .map(EmployeePayRunLineResponse::from)
                        .toList();
        return new EmployeePayRunLinesResponse(employeeId, row.getComputationError(), rowLines);
    }

    private PayRun require(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        return payRuns.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new PayRunNotFoundException(id));
    }

    /** For a status change: the row is locked, so a compute, a lock and a cancel take turns. */
    private PayRun requireForUpdate(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        return payRuns.findForUpdate(id, tenantId).orElseThrow(() -> new PayRunNotFoundException(id));
    }

    private static boolean violates(Throwable e, String constraint) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && cve.getConstraintName().contains(constraint)) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > ACTOR_MAX_LENGTH ? name.substring(0, ACTOR_MAX_LENGTH) : name;
    }
}
