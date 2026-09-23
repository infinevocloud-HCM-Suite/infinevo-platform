package com.infinevo.shared.audit;

import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Builds an audit row and writes it <strong>after the transaction commits</strong> (W-22.1).
 *
 * <p>Three things are deliberate here.
 *
 * <p><strong>Post-commit, not pre-commit</strong> - spec section 4. The Hibernate
 * {@code PostInsert}/{@code PostUpdate}/{@code PostDelete} events fire inside the transaction, so
 * writing there would leave an audit row behind a rolled-back change: a record of something that
 * never happened. The row is therefore built at event time, while the state is still in hand, and
 * persisted from {@link TransactionSynchronization#afterCommit()} in a fresh transaction.
 *
 * <p><strong>The tenant is mandatory</strong> - spec section 9, the high-likelihood risk. An
 * unbound {@link TenantContext} throws rather than dropping the row or inventing a tenant. A job
 * that writes without binding a tenant is already a defect; silence would hide it, and the RLS
 * policy would refuse the insert later anyway, at a point that no longer names the cause.
 *
 * <p><strong>Secrets never reach the row</strong> - spec section 9. {@link #REDACTED_FRAGMENTS}
 * is a deny-list matched case-insensitively against the column name, and a match is replaced with
 * {@value #REDACTED} before the row is built, not before it is displayed.
 */
@Component
public class AuditWriter {

    /**
     * Column-name fragments whose value never reaches {@code old_values}/{@code new_values}.
     * Matched anywhere in the name, so {@code password_hash} and {@code refresh_token} both hit.
     * Every entry here is long enough that an accidental substring match is implausible.
     */
    static final List<String> REDACTED_FRAGMENTS = List.of(
            "password",
            "secret",
            "token",
            "credential",
            "api_key",
            "apikey",
            "access_key",
            "private_key",
            "passphrase",
            "aadhaar",
            "passport");

    /**
     * Short names that carry a secret or an identity number but are matched as whole words only.
     * These cannot be substring-matched: {@code company_name} contains "pan", {@code shipping}
     * contains "pin", and {@code false_salt} is not the point — a fragment rule would redact
     * ordinary columns and make the audit trail useless. A name is split on {@code _} and each
     * part compared exactly, so {@code pan_number} and {@code pan} hit while {@code company_name}
     * does not.
     */
    static final List<String> REDACTED_WORDS = List.of("pan", "ssn", "otp", "pin", "salt", "cvv", "iban");

    /** What a redacted value is replaced with. */
    static final String REDACTED = "***";

    /** {@code actor_label} when no human is behind the write. */
    static final String SYSTEM_ACTOR = "system";

    private static final int ACTOR_LABEL_MAX = 100;
    private static final int ENTITY_ID_MAX = 64;
    private static final int ENTITY_SCHEMA_MAX = 32;
    private static final int ENTITY_TABLE_MAX = 64;
    private static final int TRACE_ID_MAX = 36;

    private final AuditLogRepository repository;
    private final TransactionTemplate afterCommitTransaction;

    public AuditWriter(AuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.afterCommitTransaction = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: afterCommit runs with the original transaction already completed,
        // so the insert needs one of its own.
        this.afterCommitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** One captured change, already redacted, ready to become a row. */
    public record AuditChange(
            String operation,
            String entitySchema,
            String entityTable,
            String entityId,
            List<String> changedColumns,
            Map<String, String> oldValues,
            Map<String, String> newValues) {}

    /** The actor behind a change: a Keycloak subject, or the system. */
    record Actor(UUID userId, String label) {}

    /**
     * Builds the row now and persists it once the current transaction commits. With no
     * transaction in flight the write happens immediately - there is nothing to roll back.
     */
    public void write(AuditChange change) {
        AuditLog row = buildRow(change);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    persist(row);
                }
            });
        } else {
            persist(row);
        }
    }

    private void persist(AuditLog row) {
        afterCommitTransaction.executeWithoutResult(status -> repository.save(row));
    }

    /**
     * Assembles the row: tenant from {@link TenantContext}, actor from the security context,
     * trace id from the MDC.
     *
     * @throws IllegalStateException if no tenant is bound
     */
    static AuditLog buildRow(AuditChange change) {
        UUID tenantId = TenantContext.require();
        Actor actor = resolveActor();
        List<String> changed = change.changedColumns();
        return new AuditLog(
                tenantId,
                Instant.now(),
                actor.userId(),
                truncate(actor.label(), ACTOR_LABEL_MAX),
                change.operation(),
                truncate(change.entitySchema(), ENTITY_SCHEMA_MAX),
                truncate(change.entityTable(), ENTITY_TABLE_MAX),
                truncate(change.entityId(), ENTITY_ID_MAX),
                changed == null || changed.isEmpty() ? null : changed.toArray(String[]::new),
                emptyToNull(change.oldValues()),
                emptyToNull(change.newValues()),
                truncate(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY), TRACE_ID_MAX));
    }

    /**
     * The Keycloak subject when a request is in flight, {@value #SYSTEM_ACTOR} otherwise.
     *
     * <p>{@code actor_user_id} is set only when the subject is a UUID, because {@code
     * core.user_account} does not exist to resolve anything else against until {@code W-10}
     * merges. {@code actor_label} is always set, so a system write stays attributable.
     */
    static Actor resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new Actor(null, SYSTEM_ACTOR);
        }
        String subject = null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            subject = jwt.getSubject();
        }
        if (subject == null || subject.isBlank()) {
            subject = auth.getName();
        }
        if (subject == null || subject.isBlank()) {
            return new Actor(null, SYSTEM_ACTOR);
        }
        return new Actor(parseUuidOrNull(subject), subject);
    }

    /** The properties whose value differs between the two states. */
    static List<String> changedProperties(String[] names, Object[] oldState, Object[] newState) {
        List<String> changed = new ArrayList<>();
        if (names == null || oldState == null || newState == null) {
            return changed;
        }
        for (int i = 0; i < names.length && i < oldState.length && i < newState.length; i++) {
            if (!Objects.equals(oldState[i], newState[i])) {
                changed.add(names[i]);
            }
        }
        return changed;
    }

    /**
     * Serialises {@code state} into a name/value map, keeping only the names in {@code only} when
     * it is given, and redacting anything the deny-list matches.
     */
    static Map<String, String> values(String[] names, Object[] state, List<String> only) {
        Map<String, String> values = new LinkedHashMap<>();
        if (names == null || state == null) {
            return values;
        }
        for (int i = 0; i < names.length && i < state.length; i++) {
            String name = names[i];
            if (only != null && !only.contains(name)) {
                continue;
            }
            values.put(name, isRedacted(name) ? REDACTED : serialize(state[i]));
        }
        return values;
    }

    /** Whether a column of this name has its value withheld. */
    static boolean isRedacted(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (REDACTED_FRAGMENTS.stream().anyMatch(lower::contains)) {
            return true;
        }
        for (String part : lower.split("_")) {
            if (REDACTED_WORDS.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every value becomes a string. A {@link BigDecimal} goes through {@link
     * BigDecimal#toPlainString()} - never a JSON float, which would lose the precision that
     * {@code docs/CONVENTIONS.md} section 2 exists to protect.
     */
    static String serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }

    private static Map<String, String> emptyToNull(Map<String, String> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private static UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
