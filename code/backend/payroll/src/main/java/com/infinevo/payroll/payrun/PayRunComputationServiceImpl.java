package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-29.2 §3. Three phases, none of them one long transaction:
 *
 * <ol>
 *   <li><b>Start</b> — lock the run row, move it to {@code COMPUTING}, delete its lines. A recompute
 *       starts clean, so yesterday's lines never sit beside today's.
 *   <li><b>Each included employee</b>, in a transaction of its own ({@code REQUIRES_NEW}): read the
 *       salary version in force at the period's end, run every contributor in {@code @Order}, write
 *       the lines in one batch, sum them onto the row. An employee that throws rolls back alone; the
 *       message is then written onto the row in another short transaction, and the loop carries on.
 *   <li><b>Finish</b> — run totals over the rows; {@code COMPUTED}, or {@code FAILED} with how many
 *       employees could not be computed.
 * </ol>
 *
 * <p>An unexpected error outside the per-employee loop still ends the run {@code FAILED}, so a run is
 * never left in {@code COMPUTING} by this synchronous path.
 */
@Service
public class PayRunComputationServiceImpl implements PayRunComputationService {

    private static final Logger log = LoggerFactory.getLogger(PayRunComputationServiceImpl.class);

    private static final int LINE_BATCH_SIZE = 50;
    private static final int ACTOR_MAX_LENGTH = 100;

    private final PayRunRepository payRuns;
    private final EmployeePayRunRepository employeePayRuns;
    private final EmployeePayRunLineRepository lines;
    private final EmployeeSalaryService salaryService;
    private final EmployeeService employeeService;
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
            List<PayLineContributor> contributors,
            PlatformTransactionManager transactionManager) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        // Spring injects the list sorted by @Order — that order is the order lines are written in.
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors must not be null"));
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.runTransaction = new TransactionTemplate(transactionManager);
        this.employeeTransaction = new TransactionTemplate(transactionManager);
        this.employeeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PayRunResponse compute(UUID payrunId) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        UUID tenantId = TenantContext.require();
        String actor = currentActor();

        PayRun run = Objects.requireNonNull(runTransaction.execute(status -> {
            PayRun locked =
                    payRuns.findForUpdate(payrunId, tenantId).orElseThrow(() -> new PayRunNotFoundException(payrunId));
            locked.startComputing(actor);
            lines.deleteByTenantIdAndPayrunId(tenantId, payrunId);
            return payRuns.saveAndFlush(locked);
        }));

        try {
            return computeEmployees(run, tenantId, actor);
        } catch (RuntimeException e) {
            log.error("Pay run {} in tenant {} failed outside the per-employee loop", payrunId, tenantId, e);
            try {
                finish(payrunId, tenantId, actor, List.of(), "Computation stopped: " + describe(e));
            } catch (RuntimeException secondary) {
                e.addSuppressed(secondary);
            }
            throw e;
        }
    }

    private PayRunResponse computeEmployees(PayRun run, UUID tenantId, String actor) {
        List<EmployeePayRun> rows = Objects.requireNonNull(
                runTransaction.execute(status -> employeePayRuns.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        tenantId, run.getId(), InclusionStatus.INCLUDED)));
        Map<UUID, EmployeeResponse> employees = new HashMap<>();
        for (EmployeeResponse employee :
                employeeService.listEmployedBetween(run.getPeriodStart(), run.getPeriodEnd())) {
            employees.put(employee.id(), employee);
        }

        List<EmployeePayRun> computed = new ArrayList<>(rows.size());
        int failed = 0;
        for (EmployeePayRun row : rows) {
            try {
                computed.add(employeeTransaction.execute(
                        status -> computeOne(run, row, employees.get(row.getEmployeeId()), actor)));
            } catch (RuntimeException e) {
                failed++;
                String error = describe(e);
                log.warn("Pay run {}: employee {} could not be computed: {}", run.getId(), row.getEmployeeId(), error);
                employeeTransaction.executeWithoutResult(status -> {
                    EmployeePayRun fresh = employeePayRuns.findById(row.getId()).orElseThrow();
                    fresh.recordError(error, actor, now());
                    employeePayRuns.save(fresh);
                });
            }
        }
        String reason = failed == 0 ? null : failed + " of " + rows.size() + " employees could not be computed";
        return finish(run.getId(), tenantId, actor, computed, reason);
    }

    private EmployeePayRun computeOne(PayRun run, EmployeePayRun row, EmployeeResponse employee, String actor) {
        if (employee == null) {
            throw new IllegalStateException("Employee " + row.getEmployeeId() + " is no longer employed in "
                    + run.getPeriod() + " or has been deleted");
        }
        SalaryVersionResponse version =
                salaryService.versionInForce(run.getTenantId(), row.getEmployeeId(), run.getPeriodEnd());
        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                run.getTenantId(),
                run.getId(),
                row.getId(),
                run.getPeriod(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                employee,
                version);

        List<PayLine> produced = new ArrayList<>();
        for (PayLineContributor contributor : contributors) {
            produced.addAll(contributor.contribute(ctx));
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
        fresh.recordComputation(PayRunTotals.of(produced), actor, now());
        return employeePayRuns.saveAndFlush(fresh);
    }

    private PayRunResponse finish(
            UUID payrunId, UUID tenantId, String actor, List<EmployeePayRun> computed, String failureReason) {
        BigDecimal gross = BigDecimal.ZERO.setScale(4);
        BigDecimal deductions = BigDecimal.ZERO.setScale(4);
        BigDecimal net = BigDecimal.ZERO.setScale(4);
        for (EmployeePayRun row : computed) {
            gross = gross.add(row.getGrossEarnings());
            deductions = deductions.add(row.getTotalDeductions());
            net = net.add(row.getNetPay());
        }
        BigDecimal totalGross = gross;
        BigDecimal totalDeductions = deductions;
        BigDecimal totalNet = net;
        return runTransaction.execute(status -> {
            PayRun run =
                    payRuns.findForUpdate(payrunId, tenantId).orElseThrow(() -> new PayRunNotFoundException(payrunId));
            if (failureReason == null) {
                run.completeComputation(totalGross, totalDeductions, totalNet, actor, now());
            } else {
                run.failComputation(failureReason, totalGross, totalDeductions, totalNet, actor, now());
            }
            PayRun saved = payRuns.saveAndFlush(run);
            log.info(
                    "Computed pay run {} for {} in tenant {}: status {}, net {}",
                    saved.getId(),
                    saved.getPeriod(),
                    tenantId,
                    saved.getStatus(),
                    saved.getTotalNetPay());
            return PayRunResponse.from(saved);
        });
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return PayRunServiceImpl.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > ACTOR_MAX_LENGTH ? name.substring(0, ACTOR_MAX_LENGTH) : name;
    }
}
