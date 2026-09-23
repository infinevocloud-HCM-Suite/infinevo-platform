package com.infinevo.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
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
     */
    private static EntityPersister persisterFor(Class<?> mappedClass, String... propertyNames) {
        return persisterNamed("core." + mappedClass.getSimpleName(), mappedClass, propertyNames);
    }

    private static EntityPersister persisterNamed(String entityName, Class<?> mappedClass, String... propertyNames) {
        EntityPersister persister = mock(EntityPersister.class);
        when(persister.getMappedClass()).thenAnswer(invocation -> mappedClass);
        when(persister.getEntityName()).thenReturn(entityName);
        when(persister.getPropertyNames()).thenReturn(propertyNames);
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
}
