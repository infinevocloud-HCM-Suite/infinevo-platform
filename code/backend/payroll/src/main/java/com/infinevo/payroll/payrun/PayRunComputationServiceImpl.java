package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.lop.LopPolicy;
import com.infinevo.core.lop.LopPolicyService;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisCalculator;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.component.BenefitRepository;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.SalaryComponent;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-29.2 §3, W-29.3 §3 and W-29.4 §3. One attempt of a run {@code PayRunService.compute} already
 * moved to {@code COMPUTING}, in phases, none of them one long transaction:
 *
 * <ol>
 *   <li><b>Check</b> — the run is {@code COMPUTING} at this attempt, or the message is superseded.
 *   <li><b>Once per run</b> — the period's pay inputs ({@code PayInputService.forPeriod}, one call),
 *       the policy's {@code lop_rounding}, and the ids of the pro-rata earning and benefit components
 *       (one query per catalogue). Nothing on this list is read per employee.
 *   <li><b>Each included employee not yet at this attempt</b>, in a transaction of its own
 *       ({@code REQUIRES_NEW}): lock the run's row and check the attempt again, so a worker a newer
 *       attempt has overtaken writes nothing more; delete the employee's lines, read the salary
 *       version in force at the period's end and the working-day basis (W-18.1), work out the day figures, run every
 *       contributor in {@code @Order} — each seeing the lines before it — write the lines in one batch,
 *       sum them onto the row and stamp it with the attempt. An employee that throws (no salary, no
 *       loss-of-pay policy, a lost catalogue component) rolls back alone; its lines are cleared and the
 *       message written onto the row in another short transaction, and the loop carries on. A row
 *       already at this attempt — computed before a worker died, or carried forward by a resumed
 *       attempt — is skipped, never recomputed. Progress is reported every ten employees and on the last.
 *   <li><b>Finish</b> — run totals over the rows at this attempt and the count of negative nets;
 *       {@code COMPUTED}, or {@code FAILED} with how many employees could not be computed.
 * </ol>
 *
 * <p>An unexpected error outside the per-employee loop — the database gone, the worker stopped —
 * leaves the run {@code COMPUTING} at this attempt with every finished row kept: the worker releases
 * the job for another delivery, which resumes; if every delivery fails, the officer computes the run
 * again once it is stale.
 */
@Service
public class PayRunComputationServiceImpl implements PayRunComputationService {

    private static final Logger log = LoggerFactory.getLogger(PayRunComputationServiceImpl.class);

    private static final int LINE_BATCH_SIZE = 50;

    /** Progress is reported every this many employees, and on the last (W-29.4 §9). */
    static final int PROGRESS_STEP = 10;

    private final PayRunRepository payRuns;
    private final EmployeePayRunRepository employeePayRuns;
    private final EmployeePayRunLineRepository lines;
    private final EmployeeSalaryService salaryService;
    private final EmployeeService employeeService;
    private final WorkingDayBasisCalculator basisCalculator;
    private final LopPolicyService lopPolicyService;
    private final PayInputService payInputService;
    private final EarningRepository earningRepository;
    private final BenefitRepository benefitRepository;
    private final List<PayLineContributor> contributors;
    private final TransactionTemplate runTransaction;
    private final TransactionTemplate employeeTransaction;

    @PersistenceContext
    private EntityManager entityManager;

    public PayRunComputationServiceImpl(
            PayRunRepository payRuns,
            EmployeePayRunRepository employeePayRuns,
            EmployeePayRunLineRepository lines,
            EmployeeSalaryService salaryService,
            EmployeeService employeeService,
            WorkingDayBasisCalculator basisCalculator,
            LopPolicyService lopPolicyService,
            PayInputService payInputService,
            EarningRepository earningRepository,
            BenefitRepository benefitRepository,
            List<PayLineContributor> contributors,
            PlatformTransactionManager transactionManager) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.basisCalculator = Objects.requireNonNull(basisCalculator, "basisCalculator must not be null");
        this.lopPolicyService = Objects.requireNonNull(lopPolicyService, "lopPolicyService must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.benefitRepository = Objects.requireNonNull(benefitRepository, "benefitRepository must not be null");
        // Spring injects the list sorted by @Order — that order is the order lines are written in.
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors must not be null"));
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.runTransaction = new TransactionTemplate(transactionManager);
        this.employeeTransaction = new TransactionTemplate(transactionManager);
        this.employeeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PayRunResponse compute(UUID payrunId, int attempt, String actor, ProgressReporter reporter) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reporter, "reporter must not be null");
        UUID tenantId = TenantContext.require();

        PayRun run = Objects.requireNonNull(runTransaction.execute(status -> {
            PayRun current = payRuns.findByIdAndTenantId(payrunId, tenantId)
                    .orElseThrow(() -> new PayRunNotFoundException(payrunId));
            requireAttempt(current, attempt);
            return current;
        }));

        try {
            return computeEmployees(run, attempt, tenantId, actor, reporter);
        } catch (SupersededPayRunJobException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error(
                    "Pay run {} in tenant {} stopped outside the per-employee loop; attempt {} stays COMPUTING",
                    payrunId,
                    tenantId,
                    attempt,
                    e);
            throw e;
        }
    }

    private PayRunResponse computeEmployees(
            PayRun run, int attempt, UUID tenantId, String actor, ProgressReporter reporter) {
        List<EmployeePayRun> rows = Objects.requireNonNull(
                runTransaction.execute(status -> employeePayRuns.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        tenantId, run.getId(), InclusionStatus.INCLUDED)));
        int total = rows.size();
        List<EmployeePayRun> pending =
                rows.stream().filter(row -> row.getComputedAttempt() != attempt).toList();
        int done = total - pending.size();
        if (done > 0) {
            log.info(
                    "Pay run {} attempt {}: resuming, {} of {} employees already computed",
                    run.getId(),
                    attempt,
                    done,
                    total);
        }

        if (pending.isEmpty()) {
            report(run.getId(), tenantId, attempt, done, total, reporter);
            return finish(run.getId(), tenantId, actor, attempt);
        }

        Map<UUID, EmployeeResponse> employees = new HashMap<>();
        for (EmployeeResponse employee :
                employeeService.listEmployedBetween(run.getPeriodStart(), run.getPeriodEnd())) {
            employees.put(employee.id(), employee);
        }
        RunInputs inputs = readRunInputs(run, tenantId);

        for (EmployeePayRun row : pending) {
            try {
                employeeTransaction.executeWithoutResult(
                        status -> computeOne(run, row, employees.get(row.getEmployeeId()), inputs, attempt, actor));
            } catch (SupersededPayRunJobException e) {
                // Overtaken, not failed: the row belongs to the newer attempt now.
                throw e;
            } catch (RuntimeException e) {
                String error = describe(e);
                log.warn("Pay run {}: employee {} could not be computed: {}", run.getId(), row.getEmployeeId(), error);
                employeeTransaction.executeWithoutResult(status -> {
                    requireCurrentAttempt(run.getId(), tenantId, attempt);
                    lines.deleteByTenantIdAndEmployeePayrunId(tenantId, row.getId());
                    EmployeePayRun fresh = employeePayRuns.findById(row.getId()).orElseThrow();
                    fresh.recordError(error, attempt, actor, now());
                    employeePayRuns.save(fresh);
                });
            }
            done++;
            if (done % PROGRESS_STEP == 0 || done == total) {
                report(run.getId(), tenantId, attempt, done, total, reporter);
            }
        }
        return finish(run.getId(), tenantId, actor, attempt);
    }

    /**
     * The run's own counter first, then the caller's reporter — the job's percentage on the worker. A
     * worker that was only slow, not dead, and has been overtaken by a newer attempt stops here instead
     * of writing over the newer attempt's progress: the row is locked, so a resume cannot slip in
     * between the check and the write.
     */
    private void report(UUID payrunId, UUID tenantId, int attempt, int done, int total, ProgressReporter reporter) {
        runTransaction.executeWithoutResult(status -> {
            PayRun run = requireCurrentAttempt(payrunId, tenantId, attempt);
            run.reportProgress(done);
            payRuns.save(run);
        });
        reporter.report(done, total);
    }

    /**
     * The run, locked for the rest of the caller's transaction and still {@code COMPUTING} at this
     * attempt. A resume takes the same lock before it moves the attempt on, so whatever the caller
     * writes next is written before the newer attempt starts, or not at all.
     */
    private PayRun requireCurrentAttempt(UUID payrunId, UUID tenantId, int attempt) {
        PayRun run = payRuns.findForUpdate(payrunId, tenantId).orElseThrow(() -> new PayRunNotFoundException(payrunId));
        requireAttempt(run, attempt);
        return run;
    }

    private static void requireAttempt(PayRun run, int attempt) {
        if (run.getStatus() != PayRunStatus.COMPUTING || run.getComputeAttempt() != attempt) {
            throw new SupersededPayRunJobException(run.getId(), attempt, run.getStatus(), run.getComputeAttempt());
        }
    }

    /** What every employee of the run shares, read once before the loop (W-29.3 §4, W-55). */
    private record RunInputs(
            Map<UUID, List<PayInputResponse>> payInputsByEmployee, LopRounding lopRounding, Set<UUID> proRataIds) {}

    private RunInputs readRunInputs(PayRun run, UUID tenantId) {
        Map<UUID, List<PayInputResponse>> byEmployee = new HashMap<>();
        for (PayInputResponse row : payInputService.forPeriod(run.getPeriod()).rows()) {
            byEmployee
                    .computeIfAbsent(row.employeeId(), id -> new ArrayList<>())
                    .add(row);
        }
        LopRounding rounding = lopPolicyService
                .findPolicyInForceEntity(tenantId, run.getPeriodEnd())
                .map(LopPolicy::getLopRounding)
                .orElse(LopRounding.HALF_UP_2);
        Set<UUID> proRata = new HashSet<>();
        runTransaction.executeWithoutResult(status -> {
            // A variable earning is never scaled, whatever its flag says (W-29.3 §3).
            earningRepository.findAllByTenantIdAndProRataTrueAndDeletedFalse(tenantId).stream()
                    .filter(earning -> !earning.isVariable())
                    .map(SalaryComponent::getId)
                    .forEach(proRata::add);
            benefitRepository.findAllByTenantIdAndProRataTrueAndDeletedFalse(tenantId).stream()
                    .map(SalaryComponent::getId)
                    .forEach(proRata::add);
        });
        return new RunInputs(byEmployee, rounding, proRata);
    }

    private void computeOne(
            PayRun run, EmployeePayRun row, EmployeeResponse employee, RunInputs inputs, int attempt, String actor) {
        requireCurrentAttempt(run.getId(), run.getTenantId(), attempt);
        // A row an earlier attempt computed, or failed on, starts clean.
        lines.deleteByTenantIdAndEmployeePayrunId(run.getTenantId(), row.getId());
        if (employee == null) {
            throw new IllegalStateException("Employee " + row.getEmployeeId() + " is no longer employed in "
                    + run.getPeriod() + " or has been deleted");
        }
        SalaryVersionResponse version =
                salaryService.versionInForce(run.getTenantId(), row.getEmployeeId(), run.getPeriodEnd());
        // No policy in force throws NoLopPolicyException: this employee fails, the loop carries on.
        WorkingDayBasisResponse basis =
                basisCalculator.basisFor(run.getTenantId(), run.getPeriod(), row.getEmployeeId());
        List<PayInputResponse> payInputs = inputs.payInputsByEmployee().getOrDefault(row.getEmployeeId(), List.of());
        PayRunDays days = PayRunDays.of(
                basis.payableDays(),
                PayInputLineContributor.netLopDays(payInputs),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                employee.dateOfJoining(),
                employee.terminationDate());
        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                run.getTenantId(),
                run.getId(),
                row.getId(),
                run.getPeriod(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                employee,
                version,
                basis,
                inputs.lopRounding(),
                payInputs,
                days,
                inputs.proRataIds(),
                List.of());

        List<PayLine> produced = new ArrayList<>();
        for (PayLineContributor contributor : contributors) {
            produced.addAll(contributor.contribute(ctx.withPriorLines(produced)));
        }

        // One batched INSERT for this employee's lines (W-55), not one round trip per line.
        entityManager.unwrap(Session.class).setJdbcBatchSize(LINE_BATCH_SIZE);
        List<EmployeePayRunLine> entities = new ArrayList<>(produced.size());
        for (int i = 0; i < produced.size(); i++) {
            entities.add(new EmployeePayRunLine(
                    run.getTenantId(), row.getId(), run.getId(), produced.get(i), (i + 1) * 10, actor));
        }
        lines.saveAll(entities);

        EmployeePayRun fresh = employeePayRuns.findById(row.getId()).orElseThrow();
        fresh.recordComputation(
                PayRunTotals.of(produced),
                days,
                PayInputLineContributor.unpricedCount(payInputs),
                attempt,
                actor,
                now());
        employeePayRuns.saveAndFlush(fresh);
    }

    /** Totals over the rows this attempt computed; {@code COMPUTED}, or {@code FAILED} when a row failed. */
    private PayRunResponse finish(UUID payrunId, UUID tenantId, String actor, int attempt) {
        return runTransaction.execute(status -> {
            PayRun run =
                    payRuns.findForUpdate(payrunId, tenantId).orElseThrow(() -> new PayRunNotFoundException(payrunId));
            requireAttempt(run, attempt);
            List<EmployeePayRun> rows = employeePayRuns.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                    tenantId, payrunId, InclusionStatus.INCLUDED);
            BigDecimal gross = BigDecimal.ZERO.setScale(4);
            BigDecimal deductions = BigDecimal.ZERO.setScale(4);
            BigDecimal net = BigDecimal.ZERO.setScale(4);
            int negative = 0;
            int failed = 0;
            for (EmployeePayRun row : rows) {
                if (row.getComputedAttempt() != attempt || row.getComputationError() != null) {
                    failed++;
                    continue;
                }
                gross = gross.add(row.getGrossEarnings());
                deductions = deductions.add(row.getTotalDeductions());
                net = net.add(row.getNetPay());
                if (row.getNetPay().signum() < 0) {
                    negative++;
                }
            }
            if (failed == 0) {
                run.completeComputation(gross, deductions, net, negative, actor, now());
            } else {
                run.failComputation(
                        failed + " of " + rows.size() + " employees could not be computed",
                        gross,
                        deductions,
                        net,
                        negative,
                        actor,
                        now());
            }
            PayRun saved = payRuns.saveAndFlush(run);
            log.info(
                    "Computed pay run {} for {} in tenant {}, attempt {}: status {}, net {}",
                    saved.getId(),
                    saved.getPeriod(),
                    tenantId,
                    attempt,
                    saved.getStatus(),
                    saved.getTotalNetPay());
            return PayRunResponse.from(saved);
        });
    }

    /** {@code ExceptionName: message} — the row says what failed, e.g. {@code NoLopPolicyException}. */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        String name = e.getClass().getSimpleName();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
