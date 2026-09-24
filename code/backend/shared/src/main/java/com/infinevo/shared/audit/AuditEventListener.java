package com.infinevo.shared.audit;

import jakarta.persistence.PersistenceUnitUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

/**
 * Turns a Hibernate write into an audit row (W-22.1).
 *
 * <p><strong>Opt-in.</strong> An entity whose class does not carry {@link Audited} is skipped
 * before anything else happens, so the cost of the listener on an unaudited batch is one
 * annotation lookup per row - spec section 9, first risk.
 *
 * <p>The event fires inside the transaction, where the old and new state are still available.
 * {@link AuditWriter} takes it from there and defers the insert to after commit, so a rolled-back
 * transaction leaves nothing behind.
 *
 * <p>The writer arrives as a {@link Supplier} and not as a constructor argument on purpose. The
 * listener is registered while Hibernate builds the {@code SessionFactory}, and {@link
 * AuditWriter} depends on a repository that does not exist until that build has finished.
 * Resolving it lazily, on the first event, breaks the cycle.
 */
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    /**
     * There is no fallback schema. The platform has four — {@code reference}, {@code core},
     * {@code hrms}, {@code payroll} — and no {@code public} schema at all
     * ({@code infra/postgres/02-schemas.sql}). An audited entity that does not qualify its
     * table therefore has no schema this listener could guess, and recording a plausible
     * wrong one writes bad audit data with no error. It fails loudly instead, the same
     * posture {@code AuditWriter} takes for an unbound tenant.
     */
    static final String NO_DEFAULT_SCHEMA = null;

    private final Supplier<AuditWriter> writer;

    public AuditEventListener(Supplier<AuditWriter> writer) {
        this.writer = writer;
    }

    /** For tests and for any caller that already holds the writer. */
    public AuditEventListener(AuditWriter writer) {
        this(() -> writer);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!isAudited(event.getPersister())) {
            return;
        }
        AuditWriter.Column[] columns = columnNames(event.getPersister());
        writer.get()
                .write(change(
                        "INSERT",
                        event.getPersister(),
                        event.getId(),
                        names(columns),
                        null,
                        AuditWriter.values(
                                columns,
                                identifiers(event.getPersister(), event.getState(), event.getSession()),
                                null)));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!isAudited(event.getPersister())) {
            return;
        }
        AuditWriter.Column[] columns = columnNames(event.getPersister());
        Object[] oldState = identifiers(event.getPersister(), event.getOldState(), event.getSession());
        Object[] newState = identifiers(event.getPersister(), event.getState(), event.getSession());
        List<String> changed = AuditWriter.changedProperties(columns, oldState, newState);
        if (changed.isEmpty()) {
            return;
        }
        writer.get()
                .write(change(
                        "UPDATE",
                        event.getPersister(),
                        event.getId(),
                        changed,
                        AuditWriter.values(columns, oldState, changed),
                        AuditWriter.values(columns, newState, changed)));
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (!isAudited(event.getPersister())) {
            return;
        }
        AuditWriter.Column[] columns = columnNames(event.getPersister());
        writer.get()
                .write(change(
                        "DELETE",
                        event.getPersister(),
                        event.getId(),
                        null,
                        AuditWriter.values(
                                columns,
                                identifiers(event.getPersister(), event.getDeletedState(), event.getSession()),
                                null),
                        null));
    }

    /**
     * Hibernate asks whether the listener must also run for a post-commit event. It must not:
     * this listener is registered on {@code POST_INSERT}/{@code POST_UPDATE}/{@code POST_DELETE},
     * and the post-commit half of the work is done by {@link AuditWriter} through a transaction
     * synchronisation, where a failure can still be seen.
     */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    static boolean isAudited(EntityPersister persister) {
        return persister != null
                && persister.getMappedClass() != null
                && persister.getMappedClass().isAnnotationPresent(Audited.class);
    }

    private AuditWriter.AuditChange change(
            String operation,
            EntityPersister persister,
            Object id,
            List<String> changedColumns,
            Map<String, String> oldValues,
            Map<String, String> newValues) {
        String[] qualified = schemaAndTable(persister);
        return new AuditWriter.AuditChange(
                operation, qualified[0], qualified[1], String.valueOf(id), changedColumns, oldValues, newValues);
    }

    /** Splits the persister's qualified table name into schema and table. */
    static String[] schemaAndTable(EntityPersister persister) {
        String qualified = null;
        if (persister instanceof AbstractEntityPersister entityPersister) {
            qualified = entityPersister.getTableName();
        }
        if (qualified == null || qualified.isBlank()) {
            qualified = persister.getEntityName();
        }
        qualified = qualified.replace("\"", "");
        int lastDot = qualified.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalStateException("@Audited entity '" + qualified
                    + "' does not qualify its table with a schema. Declare it, for example"
                    + " @Table(name = \"" + qualified + "\", schema = \"core\")."
                    + " There is no default: see AuditEventListener.NO_DEFAULT_SCHEMA.");
        }
        return new String[] {qualified.substring(0, lastDot), qualified.substring(lastDot + 1)};
    }

    /**
     * Replaces every association value in {@code state} with the identifier of the entity it points
     * at, leaving everything else alone.
     *
     * <p><strong>Why this is not cosmetic.</strong> For a {@code @ManyToOne}, Hibernate puts the
     * <em>associated entity</em> into the event state, not the foreign-key value. The property maps
     * to exactly one column, so {@link #columnNames} resolves it, {@link AuditWriter#values} does
     * not withhold it, and {@link AuditWriter#serialize} falls through to {@code String.valueOf} -
     * which, on an entity that declares no {@code toString}, writes
     * {@code com.infinevo.core.employee.Employee@1b6d3586} into {@code core.audit_log}. The row then
     * names neither the employee whose bank account changed nor anything a reader could act on,
     * because {@code entity_id} holds the <em>section</em> row's id. Worse, the day an audited
     * entity points at one with a generated {@code toString}, that object's fields land in the audit
     * row in clear: an association is the one route the W-13.2 redaction fix does not close, since
     * it resolves to a single column and so looks ordinary.
     *
     * <p>Diffing improves too. {@link AuditWriter#changedProperties} compares old and new state, and
     * two entity instances for the same row are not always equal; two identifiers always are.
     *
     * <p>The id is read through {@link jakarta.persistence.PersistenceUnitUtil#getIdentifier}.
     * {@link EntityType}'s own {@code getIdentifier} overloads are both {@code protected} in
     * Hibernate 6, and the JPA route has the property that matters here anyway: it answers from a
     * lazy proxy without initialising it, so auditing a write never triggers a select.
     *
     * <p>An identifier that cannot be read - a proxy whose session has gone, anything that throws -
     * becomes {@value AuditWriter#REDACTED} rather than the object. Failing to resolve is not a
     * licence to serialise, which is the same posture {@link #columnNames} takes.
     */
    static Object[] identifiers(EntityPersister persister, Object[] state, SharedSessionContractImplementor session) {
        if (state == null || session == null) {
            return state;
        }
        return identifiers(persister, state, session.getFactory().getPersistenceUnitUtil());
    }

    /**
     * The half that does the work, taking only what it needs.
     *
     * <p>Separate from the session overload so it can be tested without mocking
     * {@code SessionFactoryImplementor}, which is an interface wide enough that the mock framework
     * fails on it outright. The end-to-end proof that a real {@code @ManyToOne} lands as its id is
     * {@code EmployeeDetailAuditIT} in {@code core}, against a real session.
     */
    static Object[] identifiers(EntityPersister persister, Object[] state, PersistenceUnitUtil ids) {
        if (state == null) {
            return null;
        }
        Type[] types = persister.getPropertyTypes();
        if (types == null || ids == null) {
            return state;
        }
        Object[] resolved = state.clone();
        for (int i = 0; i < resolved.length && i < types.length; i++) {
            if (resolved[i] == null || !(types[i] instanceof EntityType)) {
                continue;
            }
            try {
                resolved[i] = ids.getIdentifier(resolved[i]);
            } catch (RuntimeException e) {
                resolved[i] = AuditWriter.REDACTED;
            }
        }
        return resolved;
    }

    /** Just the names, for {@code changed_columns} on an insert - every column is new there. */
    private static List<String> names(AuditWriter.Column[] columns) {
        return Arrays.stream(columns).map(AuditWriter.Column::name).toList();
    }

    /**
     * The database column of each persistent property, in property order, so
     * {@code changed_columns} and the value maps name columns rather than Java fields.
     *
     * <p>A property that does not map to <strong>exactly one</strong> column - an
     * {@code @Embedded} component, an association over a composite key - and a property whose
     * persister throws both come back as {@link AuditWriter.Column#unresolved(String)}, carrying
     * the Java property name and {@code resolved == false}. {@link AuditWriter#values} then
     * writes {@value AuditWriter#REDACTED} for it.
     *
     * <p>This used to return the Java property name as though it were a column name, and the
     * whole object's {@code toString()} was stored under it. That was unsafe in a way no list
     * could repair: {@link AuditWriter#isRedacted(String)} matches column names, so a Java name
     * never matches, and an embedded address or any multi-column value object was written to
     * {@code core.audit_log} in clear. Redacting instead of naming loses nothing the trail needs
     * - the name is still recorded, only the value is withheld - and it fails safe for columns
     * the deny-list has never heard of. W-13.2 spec section 2 required it before the first
     * {@link Audited} entities, which hold a bank account number, an IFSC code and a PAN.
     */
    static AuditWriter.Column[] columnNames(EntityPersister persister) {
        String[] names = persister.getPropertyNames();
        AuditWriter.Column[] columns = new AuditWriter.Column[names.length];
        // Not an AbstractEntityPersister: there is no column mapping to consult at all, so
        // nothing here is resolved. Every persister Hibernate builds is one, so this is the
        // defensive branch, not the ordinary one.
        boolean mapped = persister instanceof AbstractEntityPersister;
        for (int i = 0; i < names.length; i++) {
            columns[i] = mapped
                    ? column((AbstractEntityPersister) persister, i, names[i])
                    : AuditWriter.Column.unresolved(names[i]);
        }
        return columns;
    }

    private static AuditWriter.Column column(AbstractEntityPersister persister, int index, String property) {
        try {
            String[] columns = persister.getPropertyColumnNames(index);
            if (columns != null && columns.length == 1 && columns[0] != null && !columns[0].isBlank()) {
                return AuditWriter.Column.of(columns[0].replace("\"", ""));
            }
        } catch (RuntimeException e) {
            // A persister that cannot answer is not a licence to guess - fall through.
            return AuditWriter.Column.unresolved(property);
        }
        return AuditWriter.Column.unresolved(property);
    }
}
