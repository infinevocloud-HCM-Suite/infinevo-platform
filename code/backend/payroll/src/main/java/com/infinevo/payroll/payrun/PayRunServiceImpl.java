package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.schedule.PayPeriodResponse;
import com.infinevo.payroll.schedule.PayPeriodService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
public class PayRunServiceImpl implements PayRunService {

    private static final Logger log = LoggerFactory.getLogger(PayRunServiceImpl.class);

    static final String ACTOR_SYSTEM = "system";
    private static final int ACTOR_MAX_LENGTH = 100;
    private static final String UNIQUE_PERIOD_INDEX = "uk_payrun_tenant_period";

    private final PayRunRepository payRuns;
    private final EmployeePayRunRepository employeePayRuns;
    private final PayPeriodService payPeriodService;
    private final EmployeeService employeeService;
    private final PayRunInclusionService inclusionService;
    private final PayInputService payInputService;
    private final PayRunComputationService computationService;
    private final EmployeePayRunLineRepository lines;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public PayRunServiceImpl(
            PayRunRepository payRuns,
            EmployeePayRunRepository employeePayRuns,
            PayPeriodService payPeriodService,
            EmployeeService employeeService,
            PayRunInclusionService inclusionService,
            PayInputService payInputService,
            PayRunComputationService computationService,
            EmployeePayRunLineRepository lines,
            PlatformTransactionManager transactionManager) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.payPeriodService = Objects.requireNonNull(payPeriodService, "payPeriodService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.inclusionService = Objects.requireNonNull(inclusionService, "inclusionService must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.computationService = Objects.requireNonNull(computationService, "computationService must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public PayRunResponse create(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();

        if (Boolean.TRUE.equals(readTransaction.execute(status ->
                payRuns.existsByTenantIdAndPeriodAndStatusNot(tenantId, period.toString(), PayRunStatus.CANCELLED)))) {
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

    @Override
    @Transactional(readOnly = true)
    public PayRunResponse get(UUID id) {
        return PayRunResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayRunResponse> list(PayRunStatus status, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Page<PayRun> page = status == null
                ? payRuns.findByTenantId(tenantId, pageable)
                : payRuns.findByTenantIdAndStatus(tenantId, status, pageable);
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
        PayRun run = require(id);
        // Refuse before touching the period lock, so a 409 leaves nothing behind.
        run.getStatus().requireTransitionTo(PayRunStatus.LOCKED);
        payInputService.lock(run.getPeriod());
        run.lock(currentActor(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        PayRun saved = payRuns.saveAndFlush(run);
        log.info("Locked pay run {} for {} in tenant {}", saved.getId(), saved.getPeriod(), saved.getTenantId());
        return PayRunResponse.from(saved);
    }

    @Override
    @Transactional
    public PayRunResponse cancel(UUID id) {
        PayRun run = require(id);
        run.cancel(currentActor(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        PayRun saved = payRuns.saveAndFlush(run);
        log.info("Cancelled pay run {} for {} in tenant {}", saved.getId(), saved.getPeriod(), saved.getTenantId());
        return PayRunResponse.from(saved);
    }

    /** Not {@code @Transactional}: the computation manages a transaction per phase and per employee. */
    @Override
    public PayRunResponse compute(UUID id) {
        return computationService.compute(id);
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
