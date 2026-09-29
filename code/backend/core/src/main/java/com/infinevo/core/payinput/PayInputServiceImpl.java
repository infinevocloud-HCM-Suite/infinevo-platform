package com.infinevo.core.payinput;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PayInputService} (W-19).
 */
@Service
public class PayInputServiceImpl implements PayInputService {

    private static final Logger log = LoggerFactory.getLogger(PayInputServiceImpl.class);

    /** {@code V031__pay_input.sql} — the idempotency key. */
    private static final String INDEX_SOURCE = "uk_pay_input_tenant_source";

    /** {@code V031__pay_input.sql} — one reversal per row. */
    private static final String INDEX_REVERSES = "uk_pay_input_tenant_reverses";

    /**
     * A circuit breaker on the "next open period" search, not a real limit (spec has none). Every
     * period locked ten years out would already be a configuration mistake worth failing loudly on,
     * rather than a service that loops until the heap runs out.
     */
    private static final int MAX_REDIRECT_MONTHS = 120;

    private final PayInputRepository payInputs;
    private final PayInputPeriodLockRepository locks;

    public PayInputServiceImpl(PayInputRepository payInputs, PayInputPeriodLockRepository locks) {
        this.payInputs = Objects.requireNonNull(payInputs, "payInputs must not be null");
        this.locks = Objects.requireNonNull(locks, "locks must not be null");
    }

    @Override
    @Transactional
    public PayInputResponse record(PayInputCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID tenantId = TenantContext.require();
        validate(command);

        YearMonth postedPeriod;
        if (command.runRef() != null) {
            // Tagged (W-30.1): the run's own lock applies, not the period's, and a locked run
            // refuses outright rather than redirecting — there is no "next" off-cycle run.
            if (locks.existsByTenantIdAndRunRef(tenantId, command.runRef())) {
                throw new RunLockedException(command.runRef());
            }
            postedPeriod = command.period();
        } else {
            postedPeriod = nextOpenPeriod(tenantId, command.period());
        }
        BigDecimal amount = command.amount() != null ? command.amount().toAmount() : null;

        PayInput input = new PayInput(
                tenantId,
                command.employeeId(),
                postedPeriod,
                command.kind(),
                command.quantity(),
                amount,
                command.sourceModule(),
                command.sourceRef(),
                command.runRef(),
                null,
                currentActor());

        PayInput saved = insert(input, command.sourceModule(), command.sourceRef());
        log.info(
                "Recorded pay input {} ({}) for employee {} in tenant {}, period {}{}",
                saved.getId(),
                saved.getKind(),
                saved.getEmployeeId(),
                tenantId,
                saved.getPeriod(),
                postedPeriod.equals(command.period()) ? "" : " (redirected from " + command.period() + ")");
        return PayInputResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PayInputListResponse forEmployee(UUID employeeId, YearMonth period) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();
        return toListResponse(
                payInputs.findByTenantIdAndEmployeeIdAndPeriodAndRunRefIsNull(tenantId, employeeId, period));
    }

    @Override
    @Transactional(readOnly = true)
    public PayInputListResponse forPeriod(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();
        return toListResponse(payInputs.findByTenantIdAndPeriodAndRunRefIsNull(tenantId, period));
    }

    @Override
    @Transactional(readOnly = true)
    public PayInputRunResponse forRun(UUID runRef) {
        Objects.requireNonNull(runRef, "runRef must not be null");
        UUID tenantId = TenantContext.require();
        return toRunResponse(payInputs.findByTenantIdAndRunRef(tenantId, runRef));
    }

    @Override
    @Transactional
    public void lock(YearMonth period) {
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();
        int inserted = locks.insertIfAbsent(tenantId, period.toString(), currentActor());
        if (inserted == 0) {
            log.info("Pay input period {} in tenant {} was already locked; leaving it as it was", period, tenantId);
        } else {
            log.info("Locked pay input period {} in tenant {}", period, tenantId);
        }
    }

    @Override
    @Transactional
    public void lockRun(UUID runRef, YearMonth period) {
        Objects.requireNonNull(runRef, "runRef must not be null");
        Objects.requireNonNull(period, "period must not be null");
        UUID tenantId = TenantContext.require();
        int inserted = locks.insertRunLockIfAbsent(tenantId, period.toString(), runRef, currentActor());
        if (inserted == 0) {
            log.info("Pay input run {} in tenant {} was already locked; leaving it as it was", runRef, tenantId);
        } else {
            log.info("Locked pay input run {} in tenant {} for period {}", runRef, tenantId, period);
        }
    }

    @Override
    @Transactional
    public PayInputResponse reverse(UUID id, String reason) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        PayInput original = payInputs.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));

        // One reversal per row, and never a reversal of a reversal: either would subtract a second
        // time instead of netting to zero. This check answers the sequential retry; the partial
        // unique index uk_pay_input_tenant_reverses (V031) answers two reversals racing each other,
        // and insert() below translates that collision into the same exception.
        if (original.isReversal() || payInputs.existsByTenantIdAndReversesId(tenantId, original.getId())) {
            throw new AlreadyReversedException(original.getId());
        }

        // A reversal of a tagged row stays tagged to the same run, so forRun(A)'s total nets it to
        // zero exactly as forPeriod's does for an untagged row - as long as that run is still open.
        // If it has since been locked, this is not new collection into a closed run (the case §3's
        // refuse-outright rule is for); it is a correction of what the run already collected, and a
        // mistake must stay correctable. So it falls back to an ordinary, untagged correction,
        // redirected to the next open period exactly as a locked-period reversal already is.
        UUID originalRunRef = original.getRunRef();
        boolean runStillOpen = originalRunRef != null && !locks.existsByTenantIdAndRunRef(tenantId, originalRunRef);
        UUID reversalRunRef = runStillOpen ? originalRunRef : null;
        YearMonth postedPeriod = runStillOpen ? original.getPeriod() : nextOpenPeriod(tenantId, original.getPeriod());
        PayInput reversal = new PayInput(
                tenantId,
                original.getEmployeeId(),
                postedPeriod,
                original.getKind(),
                original.getQuantity(),
                original.getAmount(),
                original.getSourceModule(),
                original.getSourceRef(),
                reversalRunRef,
                original.getId(),
                currentActor());

        // The idempotency index is scoped to non-reversal rows (V031), so this insert cannot collide
        // with the row it reverses even when both share the same (source_module, source_ref) - that
        // is the point of the partial index. It can collide with a concurrent reversal of the same
        // row, on uk_pay_input_tenant_reverses; insert() reports that as AlreadyReversedException.
        PayInput saved = insert(reversal, null, null);
        log.info(
                "Reversed pay input {} with {} in tenant {} ({}): {}",
                original.getId(),
                saved.getId(),
                tenantId,
                describeReversalOutcome(original, originalRunRef, reversalRunRef, postedPeriod),
                reason);
        return PayInputResponse.from(saved);
    }

    /** What changed for a reversal, for the log line only — never parsed, so free-form is fine. */
    private static String describeReversalOutcome(
            PayInput original, UUID originalRunRef, UUID reversalRunRef, YearMonth postedPeriod) {
        if (reversalRunRef != null) {
            return "same run";
        }
        if (originalRunRef != null) {
            return "run " + originalRunRef + " is locked, fell back to an untagged correction in "
                    + (postedPeriod.equals(original.getPeriod()) ? "the same period" : "redirected to " + postedPeriod);
        }
        return postedPeriod.equals(original.getPeriod()) ? "same period" : "redirected to " + postedPeriod;
    }

    /**
     * Inserts a row, translating the idempotency index into {@link DuplicatePayInputException} and
     * the one-reversal index into {@link AlreadyReversedException}.
     */
    private PayInput insert(PayInput input, String sourceModuleForMessage, String sourceRefForMessage) {
        try {
            return payInputs.saveAndFlush(input);
        } catch (DataIntegrityViolationException e) {
            if (namesIndex(e, INDEX_SOURCE)) {
                throw new DuplicatePayInputException(
                        sourceModuleForMessage != null ? sourceModuleForMessage : input.getSourceModule(),
                        sourceRefForMessage != null ? sourceRefForMessage : input.getSourceRef());
            }
            if (namesIndex(e, INDEX_REVERSES)) {
                throw new AlreadyReversedException(input.getReversesId());
            }
            throw e;
        }
    }

    /**
     * {@code period}, or the first later period with no lock — the late-input rule (spec §4,
     * {@code 12-core-contracts.md} §6 decision 3). Two, or any number, of consecutive locked periods
     * are all skipped in turn.
     */
    private YearMonth nextOpenPeriod(UUID tenantId, YearMonth period) {
        YearMonth candidate = period;
        int checked = 0;
        while (locks.existsByTenantIdAndPeriodAndRunRefIsNull(tenantId, candidate)) {
            if (++checked > MAX_REDIRECT_MONTHS) {
                throw new IllegalStateException("No open period found for tenant " + tenantId + " within "
                        + MAX_REDIRECT_MONTHS + " months of " + period);
            }
            candidate = candidate.plusMonths(1);
        }
        return candidate;
    }

    private PayInputListResponse toListResponse(List<PayInput> rows) {
        Map<PayInputKind, BigDecimal> quantityTotals = new EnumMap<>(PayInputKind.class);
        Map<PayInputKind, BigDecimal> amountTotals = new EnumMap<>(PayInputKind.class);
        for (PayInput row : rows) {
            int sign = row.isReversal() ? -1 : 1;
            if (row.getQuantity() != null) {
                quantityTotals.merge(
                        row.getKind(), row.getQuantity().multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
            }
            if (row.getAmount() != null) {
                amountTotals.merge(row.getKind(), row.getAmount().multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
            }
        }
        return new PayInputListResponse(
                rows.stream().map(PayInputResponse::from).toList(), quantityTotals, amountTotals);
    }

    /** {@code forRun}'s totals, one employee at a time (spec §4) — never blended across the run. */
    private PayInputRunResponse toRunResponse(List<PayInput> rows) {
        Map<UUID, Map<PayInputKind, BigDecimal>> quantityTotals = new LinkedHashMap<>();
        Map<UUID, Map<PayInputKind, BigDecimal>> amountTotals = new LinkedHashMap<>();
        for (PayInput row : rows) {
            int sign = row.isReversal() ? -1 : 1;
            if (row.getQuantity() != null) {
                quantityTotals
                        .computeIfAbsent(row.getEmployeeId(), id -> new EnumMap<>(PayInputKind.class))
                        .merge(row.getKind(), row.getQuantity().multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
            }
            if (row.getAmount() != null) {
                amountTotals
                        .computeIfAbsent(row.getEmployeeId(), id -> new EnumMap<>(PayInputKind.class))
                        .merge(row.getKind(), row.getAmount().multiply(BigDecimal.valueOf(sign)), BigDecimal::add);
            }
        }
        return new PayInputRunResponse(
                rows.stream().map(PayInputResponse::from).toList(), quantityTotals, amountTotals);
    }

    private void validate(PayInputCommand command) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        if (command.employeeId() == null) {
            fieldErrors.put("employeeId", "employeeId is required");
        }
        if (command.period() == null) {
            fieldErrors.put("period", "period is required");
        }
        if (command.kind() == null) {
            fieldErrors.put("kind", "kind is required");
        }
        if (command.sourceModule() == null || command.sourceModule().isBlank()) {
            fieldErrors.put("sourceModule", "sourceModule is required");
        }
        if (command.quantity() != null && command.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            fieldErrors.put("quantity", "quantity must be positive");
        }
        if (command.amount() != null && !command.amount().isPositive()) {
            fieldErrors.put("amount", "amount must be positive");
        }

        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(fieldErrors);
        }
    }

    /** Whether an index name appears anywhere in a throwable's cause chain. */
    private static boolean namesIndex(Throwable e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(indexName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
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
            return PayInput.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
