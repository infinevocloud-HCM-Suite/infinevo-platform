package com.infinevo.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.PersistenceUnitUtil;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W-22.1 — capture is opt-in: an entity without {@link Audited} produces no row (spec section 7).
 */
class AuditEventListenerTest {

    /** Opted in. */
    @Audited
    static class AuditedThing {}

    /** Not opted in — the default, and the case that must cost nothing. */
    static class PlainThing {}

    /**
     * A real persister always reports a schema-qualified table, so the mock does too. Returning a
     * bare class name here would have made every test exercise a path production never takes.
     *
     * <p>It is an {@link AbstractEntityPersister} for the same reason: that is what Hibernate
     * builds, and it is the only shape from which {@link AuditEventListener#columnNames} can
     * resolve a column at all. Each property is mapped to one column of its own name, the
     * ordinary case; {@link #multiColumnPersister} is the one that is not.
     */
    private static EntityPersister persisterFor(Class<?> mappedClass, String... propertyNames) {
        return persisterNamed("core." + mappedClass.getSimpleName(), mappedClass, propertyNames);
    }

    private static AbstractEntityPersister persisterNamed(
            String entityName, Class<?> mappedClass, String... propertyNames) {
        AbstractEntityPersister persister = mock(AbstractEntityPersister.class);
        when(persister.getMappedClass()).thenAnswer(invocation -> mappedClass);
        when(persister.getEntityName()).thenReturn(entityName);
        when(persister.getPropertyNames()).thenReturn(propertyNames);
        for (int i = 0; i < propertyNames.length; i++) {
            when(persister.getPropertyColumnNames(i)).thenReturn(new String[] {propertyNames[i]});
        }
        return persister;
    }

    @Test
    @DisplayName("Insert of an entity without @Audited writes nothing")
    void insertOfUnauditedEntityWritesNothing() {
        AuditWriter writer = mock(AuditWriter.class);
        PostInsertEvent event = mock(PostInsertEvent.class);
        EntityPersister persister = persisterFor(PlainThing.class, "name");
        when(event.getPersister()).thenReturn(persister);

        new AuditEventListener(writer).onPostInsert(event);

        verify(writer, never()).write(any());
    }

    @Test
    @DisplayName("Update of an entity without @Audited writes nothing")
    void updateOfUnauditedEntityWritesNothing() {
        AuditWriter writer = mock(AuditWriter.class);
        PostUpdateEvent event = mock(PostUpdateEvent.class);
        EntityPersister persister = persisterFor(PlainThing.class, "name");
        when(event.getPersister()).thenReturn(persister);

        new AuditEventListener(writer).onPostUpdate(event);

        verify(writer, never()).write(any());
    }

    @Test
    @DisplayName("Delete of an entity without @Audited writes nothing")
    void deleteOfUnauditedEntityWritesNothing() {
        AuditWriter writer = mock(AuditWriter.class);
        PostDeleteEvent event = mock(PostDeleteEvent.class);
        EntityPersister persister = persisterFor(PlainThing.class, "name");
        when(event.getPersister()).thenReturn(persister);

        new AuditEventListener(writer).onPostDelete(event);

        verify(writer, never()).write(any());
    }

    @Test
    @DisplayName("Insert of an @Audited entity writes one row carrying the new values")
    void insertOfAuditedEntityWritesOneRow() {
        AuditWriter writer = mock(AuditWriter.class);
        PostInsertEvent event = mock(PostInsertEvent.class);
        EntityPersister persister = persisterFor(AuditedThing.class, "name", "amount");
        when(event.getPersister()).thenReturn(persister);
        when(event.getId()).thenReturn(7L);
        when(event.getState()).thenReturn(new Object[] {"Acme", new BigDecimal("1250.50")});

        new AuditEventListener(writer).onPostInsert(event);

        ArgumentCaptor<AuditWriter.AuditChange> captured = ArgumentCaptor.forClass(AuditWriter.AuditChange.class);
        verify(writer).write(captured.capture());
        AuditWriter.AuditChange change = captured.getValue();
        assertThat(change.operation()).isEqualTo("INSERT");
        assertThat(change.entityId()).isEqualTo("7");
        assertThat(change.oldValues()).isNull();
        assertThat(change.newValues()).containsEntry("name", "Acme").containsEntry("amount", "1250.50");
    }

    @Test
    @DisplayName("Update of an @Audited entity writes only the changed columns")
    void updateOfAuditedEntityWritesOnlyChangedColumns() {
        AuditWriter writer = mock(AuditWriter.class);
        PostUpdateEvent event = mock(PostUpdateEvent.class);
        EntityPersister persister = persisterFor(AuditedThing.class, "name", "status");
        when(event.getPersister()).thenReturn(persister);
        when(event.getId()).thenReturn(7L);
        when(event.getOldState()).thenReturn(new Object[] {"Acme", "ACTIVE"});
        when(event.getState()).thenReturn(new Object[] {"Acme Ltd", "ACTIVE"});

        new AuditEventListener(writer).onPostUpdate(event);

        ArgumentCaptor<AuditWriter.AuditChange> captured = ArgumentCaptor.forClass(AuditWriter.AuditChange.class);
        verify(writer).write(captured.capture());
        AuditWriter.AuditChange change = captured.getValue();
        assertThat(change.operation()).isEqualTo("UPDATE");
        assertThat(change.changedColumns()).containsExactly("name");
        assertThat(change.oldValues()).containsExactly(org.assertj.core.api.Assertions.entry("name", "Acme"));
        assertThat(change.newValues()).containsExactly(org.assertj.core.api.Assertions.entry("name", "Acme Ltd"));
    }

    @Test
    @DisplayName("An update that changed nothing writes nothing")
    void updateWithNoChangeWritesNothing() {
        AuditWriter writer = mock(AuditWriter.class);
        PostUpdateEvent event = mock(PostUpdateEvent.class);
        EntityPersister persister = persisterFor(AuditedThing.class, "name");
        when(event.getPersister()).thenReturn(persister);
        when(event.getOldState()).thenReturn(new Object[] {"Acme"});
        when(event.getState()).thenReturn(new Object[] {"Acme"});

        new AuditEventListener(writer).onPostUpdate(event);

        verify(writer, never()).write(any());
    }

    @Test
    @DisplayName("Delete of an @Audited entity writes one row carrying the old values")
    void deleteOfAuditedEntityWritesOneRow() {
        AuditWriter writer = mock(AuditWriter.class);
        PostDeleteEvent event = mock(PostDeleteEvent.class);
        EntityPersister persister = persisterFor(AuditedThing.class, "name");
        when(event.getPersister()).thenReturn(persister);
        when(event.getId()).thenReturn(7L);
        when(event.getDeletedState()).thenReturn(new Object[] {"Acme"});

        new AuditEventListener(writer).onPostDelete(event);

        ArgumentCaptor<AuditWriter.AuditChange> captured = ArgumentCaptor.forClass(AuditWriter.AuditChange.class);
        verify(writer).write(captured.capture());
        assertThat(captured.getValue().operation()).isEqualTo("DELETE");
        assertThat(captured.getValue().oldValues()).containsEntry("name", "Acme");
        assertThat(captured.getValue().newValues()).isNull();
    }

    /**
     * A persister whose {@code homeAddress} property spans two columns - the shape an
     * {@code @Embedded} component takes - while {@code name} maps to one as usual.
     */
    private static AbstractEntityPersister multiColumnPersister() {
        AbstractEntityPersister persister =
                persisterNamed("core.AuditedThing", AuditedThing.class, "name", "homeAddress");
        when(persister.getPropertyColumnNames(1)).thenReturn(new String[] {"address_line1", "zip_code"});
        return persister;
    }

    /** Stands in for the embedded value object. Its toString must never reach an audit row. */
    private record HomeAddress(String line1, String zip) {
        @Override
        public String toString() {
            return line1 + ", " + zip;
        }
    }

    @Test
    @DisplayName("A property spanning several columns is redacted, not stored under its Java name")
    void multiColumnPropertyIsRedacted() {
        // Before W-13.2 this wrote {"homeAddress": "12 Secret Lane, 560001"} into core.audit_log:
        // the fallback used the Java property name, which no deny-list entry can match.
        AuditWriter writer = mock(AuditWriter.class);
        PostInsertEvent event = mock(PostInsertEvent.class);
        // Built before the when(...), not inside it: stubbing one mock inside another's
        // unfinished stubbing is what Mockito calls UnfinishedStubbing.
        AbstractEntityPersister persister = multiColumnPersister();
        when(event.getPersister()).thenReturn(persister);
        when(event.getId()).thenReturn(7L);
        when(event.getState()).thenReturn(new Object[] {"Acme", new HomeAddress("12 Secret Lane", "560001")});

        new AuditEventListener(writer).onPostInsert(event);

        ArgumentCaptor<AuditWriter.AuditChange> captured = ArgumentCaptor.forClass(AuditWriter.AuditChange.class);
        verify(writer).write(captured.capture());
        AuditWriter.AuditChange change = captured.getValue();
        assertThat(change.newValues()).containsEntry("homeAddress", AuditWriter.REDACTED);
        assertThat(change.newValues()).containsEntry("name", "Acme");
        assertThat(change.newValues().values()).doesNotContain("12 Secret Lane, 560001");
        // The name is still on the row, so the trail says what changed.
        assertThat(change.changedColumns()).contains("homeAddress");
    }

    @Test
    @DisplayName("A persister that throws yields an unresolved column, never a guessed one")
    void persisterFailureRedactsRatherThanGuesses() {
        AbstractEntityPersister persister =
                persisterNamed("core.AuditedThing", AuditedThing.class, "name", "secretish");
        when(persister.getPropertyColumnNames(1)).thenThrow(new IllegalStateException("no such property"));

        AuditWriter.Column[] columns = AuditEventListener.columnNames(persister);

        assertThat(columns[0]).isEqualTo(AuditWriter.Column.of("name"));
        assertThat(columns[1]).isEqualTo(AuditWriter.Column.unresolved("secretish"));
        assertThat(AuditWriter.values(columns, new Object[] {"Acme", "leak-me"}, null))
                .containsEntry("secretish", AuditWriter.REDACTED);
    }

    @Test
    @DisplayName("An update of a multi-column property records the change but withholds both values")
    void multiColumnUpdateWithholdsOldAndNewValues() {
        AuditWriter writer = mock(AuditWriter.class);
        PostUpdateEvent event = mock(PostUpdateEvent.class);
        AbstractEntityPersister persister = multiColumnPersister();
        when(event.getPersister()).thenReturn(persister);
        when(event.getId()).thenReturn(7L);
        when(event.getOldState()).thenReturn(new Object[] {"Acme", new HomeAddress("12 Secret Lane", "560001")});
        when(event.getState()).thenReturn(new Object[] {"Acme", new HomeAddress("9 Other Road", "560002")});

        new AuditEventListener(writer).onPostUpdate(event);

        ArgumentCaptor<AuditWriter.AuditChange> captured = ArgumentCaptor.forClass(AuditWriter.AuditChange.class);
        verify(writer).write(captured.capture());
        AuditWriter.AuditChange change = captured.getValue();
        assertThat(change.changedColumns()).containsExactly("homeAddress");
        assertThat(change.oldValues()).containsExactly(org.assertj.core.api.Assertions.entry("homeAddress", "***"));
        assertThat(change.newValues()).containsExactly(org.assertj.core.api.Assertions.entry("homeAddress", "***"));
    }

    /** Opted in on the parent only — the shape a @MappedSuperclass or a base entity takes. */
    static class SubclassOfAuditedThing extends AuditedThing {}

    @Test
    @DisplayName("@Audited is inherited, so a subclass of an audited base is still captured")
    void auditedIsInheritedBySubclasses() {
        // Without @Inherited on the annotation this returns false and every subclass of an
        // audited base is silently never captured - a guard that reads as on and is off.
        assertThat(AuditEventListener.isAudited(persisterFor(SubclassOfAuditedThing.class)))
                .isTrue();
        assertThat(AuditEventListener.isAudited(persisterFor(PlainThing.class))).isFalse();
    }

    @Test
    @DisplayName("An audited entity whose table names no schema fails loudly, it does not guess")
    void unqualifiedTableNameThrowsRatherThanGuessing() {
        // There is no `public` schema in this platform, so a fallback would write an audit row
        // naming a schema that does not exist - wrong data, and no error to notice it by.
        EntityPersister persister = persisterNamed("AuditedThing", AuditedThing.class, "name");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditEventListener.schemaAndTable(persister))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not qualify its table with a schema");
    }

    /** An entity with no {@code toString} of its own - what every entity in this platform is. */
    private static Object entityWithDefaultToString() {
        return new Object() {
            @Override
            public String toString() {
                return "com.infinevo.core.employee.Employee@1b6d3586";
            }
        };
    }

    @Test
    @DisplayName("A @ManyToOne is captured as the id it points at, never the entity's toString")
    void associationIsCapturedAsItsIdentifier() {
        // W-13.2 review, F-1. For an association Hibernate puts the associated ENTITY into the
        // event state, not the foreign key. The property still maps to exactly one column, so it
        // resolves, is not withheld, and used to be serialised with String.valueOf - writing
        // "com.infinevo.core.employee.Employee@1b6d3586" where the employee's id belongs. The
        // audit row then named nobody, because entity_id holds the section row's id.
        AbstractEntityPersister persister =
                persisterNamed("core.employee_bank", AuditedThing.class, "employee", "bank_name");
        Type[] types = {mock(EntityType.class), mock(Type.class)};
        when(persister.getPropertyTypes()).thenReturn(types);
        UUID employeeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PersistenceUnitUtil ids = mock(PersistenceUnitUtil.class);
        when(ids.getIdentifier(any())).thenReturn(employeeId);
        Object[] state = {entityWithDefaultToString(), "Bank of Acme"};

        Object[] resolved = AuditEventListener.identifiers(persister, state, ids);

        assertThat(AuditWriter.values(AuditEventListener.columnNames(persister), resolved, null))
                .as("the audit row must name the employee the section belongs to")
                .containsEntry("employee", employeeId.toString())
                .containsEntry("bank_name", "Bank of Acme");
        assertThat(resolved[0])
                .as("a JVM identity string names nobody and may carry the object's fields")
                .isEqualTo(employeeId);
    }

    @Test
    @DisplayName("An identifier that cannot be read is redacted, not serialised")
    void unreadableIdentifierIsRedacted() {
        AbstractEntityPersister persister = persisterNamed("core.employee_bank", AuditedThing.class, "employee");
        Type[] types = {mock(EntityType.class)};
        when(persister.getPropertyTypes()).thenReturn(types);
        PersistenceUnitUtil ids = mock(PersistenceUnitUtil.class);
        when(ids.getIdentifier(any())).thenThrow(new IllegalStateException("session closed"));
        Object[] state = {entityWithDefaultToString()};

        Object[] resolved = AuditEventListener.identifiers(persister, state, ids);

        assertThat(AuditWriter.values(AuditEventListener.columnNames(persister), resolved, null))
                .as("failing to resolve an id is not a licence to serialise the object")
                .containsEntry("employee", AuditWriter.REDACTED);
    }

    @Test
    @DisplayName("A non-association property is left exactly as it was")
    void ordinaryPropertiesAreUntouched() {
        AbstractEntityPersister persister =
                persisterNamed("core.employee_bank", AuditedThing.class, "bank_name", "amount");
        Type[] types = {mock(Type.class), mock(Type.class)};
        when(persister.getPropertyTypes()).thenReturn(types);
        PersistenceUnitUtil ids = mock(PersistenceUnitUtil.class);
        Object[] state = {"Bank of Acme", new BigDecimal("1200.5000")};

        Object[] resolved = AuditEventListener.identifiers(persister, state, ids);

        assertThat(resolved).containsExactly("Bank of Acme", new BigDecimal("1200.5000"));
        verifyNoInteractions(ids);
    }
}
