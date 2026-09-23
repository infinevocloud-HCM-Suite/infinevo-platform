package com.infinevo.shared.audit;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;

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
        String[] columns = columnNames(event.getPersister());
        writer.get()
                .write(change(
                        "INSERT",
                        event.getPersister(),
                        event.getId(),
                        List.of(columns),
                        null,
                        AuditWriter.values(columns, event.getState(), null)));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!isAudited(event.getPersister())) {
            return;
        }
        String[] columns = columnNames(event.getPersister());
        List<String> changed = AuditWriter.changedProperties(columns, event.getOldState(), event.getState());
        if (changed.isEmpty()) {
            return;
        }
        writer.get()
                .write(change(
                        "UPDATE",
                        event.getPersister(),
                        event.getId(),
                        changed,
                        AuditWriter.values(columns, event.getOldState(), changed),
                        AuditWriter.values(columns, event.getState(), changed)));
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (!isAudited(event.getPersister())) {
            return;
        }
        String[] columns = columnNames(event.getPersister());
        writer.get()
                .write(change(
                        "DELETE",
                        event.getPersister(),
                        event.getId(),
                        null,
                        AuditWriter.values(columns, event.getDeletedState(), null),
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
     * The database column name of each persistent property, in property order, so
     * {@code changed_columns} and the value maps name columns rather than Java fields. Falls back
     * to the property name for anything the persister cannot resolve to a single column - a
     * component, or an association spanning several.
     */
    static String[] columnNames(EntityPersister persister) {
        String[] names = persister.getPropertyNames();
        if (!(persister instanceof AbstractEntityPersister entityPersister)) {
            return names;
        }
        String[] columns = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            columns[i] = columnName(entityPersister, i, names[i]);
        }
        return columns;
    }

    private static String columnName(AbstractEntityPersister persister, int index, String property) {
        try {
            String[] columns = persister.getPropertyColumnNames(index);
            if (columns != null && columns.length == 1 && columns[0] != null && !columns[0].isBlank()) {
                return columns[0].replace("\"", "");
            }
        } catch (RuntimeException e) {
            return property;
        }
        return property;
    }
}
