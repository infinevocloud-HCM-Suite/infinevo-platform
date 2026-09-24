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
 * {@value #REDACTED} before the row is built, not before it is displayed. A deny-list only covers
 * what it has heard of, so it is not the whole defence: any property whose database column could
 * not be resolved is redacted outright, whatever it is called - see {@link Column}.
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
            "passport",
            // W-13.2: core.employee_bank.bank_account_number and ifsc_code are the first two
            // production columns this list had never heard of. Both are long enough that an
            // accidental substring match is implausible, so they belong here and not in
            // REDACTED_WORDS.
            "account_number",
            "ifsc",
            // W-13.2 review, F-2: core.employee_identification holds four more identity numbers
            // that this list had never heard of - social_insurance_number, personal_tax_id,
            // id_document_number and address_document_number. PAN and Aadhaar were covered only
            // because V017__employee_identification.sql spells those columns to match this list;
            // their four siblings on the same row were being written to core.audit_log in clear,
            // where readonly_user can read them. "document_number" catches both document columns
            // without reaching an ordinary count or reference, and "tax_id" and "social_insurance"
            // are each long enough that an accidental match is implausible.
            "social_insurance",
            "tax_id",
            "document_number");

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

    /**
     * One audited property, paired with whether the persister could resolve it to exactly one
     * database column.
     *
     * <p><strong>Why the flag exists.</strong> {@code AuditEventListener.columnNames} used to
     * fall back to the Java property name whenever a property did not map to a single column -
     * an {@code @Embedded} component, an association over a composite key, or a persister that
     * threw. The value was then serialised under that Java name with {@code String.valueOf}, and
     * {@link #isRedacted(String)} matches <em>column</em> names, so the deny-list could never
     * match it: an embedded address, or any multi-column value object, reached
     * {@code core.audit_log} in clear. W-22.1 deferred this; W-13.2 could not, because the first
     * entities to carry {@link Audited} hold a bank account number, an IFSC code and a PAN
     * (spec section 2).
     *
     * <p><strong>What {@code resolved == false} means.</strong> The name is still recorded - in
     * {@code changed_columns} and as the key of the value map - so the trail still says
     * <em>what</em> changed. Only the value is withheld: {@link #values} writes {@value #REDACTED}
     * for it whatever it is called. That is fail-safe by construction rather than by vigilance,
     * because it cannot be defeated by a column the deny-list has not heard of. A multi-column
     * property is redacted rather than named: naming it was the unsafe half, since a name the
     * deny-list cannot match is a name that buys nothing.
     */
    public record Column(String name, boolean resolved) {

        /** A property whose single database column name is known. */
        public static Column of(String name) {
            return new Column(name, true);
        }

        /** A property whose column could not be determined; the value is withheld. */
        public static Column unresolved(String name) {
            return new Column(name, false);
        }
    }

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

    /**
     * The properties whose value differs between the two states, by name. An unresolved column
     * is diffed like any other - withholding the value does not mean hiding that it changed.
     */
    static List<String> changedProperties(Column[] columns, Object[] oldState, Object[] newState) {
        List<String> changed = new ArrayList<>();
        if (columns == null || oldState == null || newState == null) {
            return changed;
        }
        for (int i = 0; i < columns.length && i < oldState.length && i < newState.length; i++) {
            if (!Objects.equals(oldState[i], newState[i])) {
                changed.add(columns[i].name());
            }
        }
        return changed;
    }

    /**
     * Serialises {@code state} into a name/value map, keeping only the names in {@code only} when
     * it is given.
     *
     * <p>A value is withheld for either of two reasons, and the second is the one that cannot be
     * got wrong: the column name matches the deny-list, <em>or</em> the column could not be
     * resolved at all ({@link Column}). The unresolved case is checked first and without
     * consulting any list, because the whole point is that its name is not a column name and so
     * proves nothing about what the object holds.
     */
    static Map<String, String> values(Column[] columns, Object[] state, List<String> only) {
        Map<String, String> values = new LinkedHashMap<>();
        if (columns == null || state == null) {
            return values;
        }
        for (int i = 0; i < columns.length && i < state.length; i++) {
            Column column = columns[i];
            if (only != null && !only.contains(column.name())) {
                continue;
            }
            boolean withhold = !column.resolved() || isRedacted(column.name());
            values.put(column.name(), withhold ? REDACTED : serialize(state[i]));
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
