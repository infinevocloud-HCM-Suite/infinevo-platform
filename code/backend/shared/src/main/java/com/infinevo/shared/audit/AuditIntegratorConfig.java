package com.infinevo.shared.audit;

import java.util.List;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AuditEventListener} with Hibernate (W-22.1, spec section 4).
 *
 * <p>A listener, not Envers: Envers writes a shadow table per audited entity, and the data model
 * allots {@code CORE-14} exactly one table - {@code docs/target-state/02-data-model.md:305}. One
 * listener writes every entity into {@code core.audit_log}.
 *
 * <p>The listener is <em>appended</em> to the existing chain rather than set on it, so nothing
 * Hibernate or Spring already registered is displaced.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HibernatePropertiesCustomizer.class)
public class AuditIntegratorConfig {

    /**
     * Hands Hibernate an {@link Integrator} at bootstrap.
     *
     * <p>{@link ObjectProvider} rather than the bean itself: this customiser is applied while the
     * {@code EntityManagerFactory} is being built, and {@link AuditWriter} needs a repository that
     * only exists once it has been. The writer is fetched on the first event instead.
     */
    @Bean
    public HibernatePropertiesCustomizer auditIntegratorCustomizer(ObjectProvider<AuditWriter> writerProvider) {
        AuditEventListener listener = new AuditEventListener(writerProvider::getObject);
        return properties -> properties.put(
                JpaSettings.INTEGRATOR_PROVIDER, (IntegratorProvider) () -> List.of(new AuditIntegrator(listener)));
    }

    /** Appends the audit listener to the three write events. */
    static class AuditIntegrator implements Integrator {

        private final AuditEventListener listener;

        AuditIntegrator(AuditEventListener listener) {
            this.listener = listener;
        }

        @Override
        public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sf) {
            EventListenerRegistry registry = sf.getServiceRegistry().requireService(EventListenerRegistry.class);
            registry.appendListeners(EventType.POST_INSERT, listener);
            registry.appendListeners(EventType.POST_UPDATE, listener);
            registry.appendListeners(EventType.POST_DELETE, listener);
        }

        @Override
        public void disintegrate(
                SessionFactoryImplementor sf, org.hibernate.service.spi.SessionFactoryServiceRegistry registry) {
            // Nothing held open: the listener has no resources of its own.
        }
    }
}
