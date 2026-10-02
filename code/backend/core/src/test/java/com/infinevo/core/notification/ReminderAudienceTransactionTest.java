package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * W-43.1: an audience that implements only {@code resolve} still reads inside a transaction when the sweep calls
 * {@code recipients}.
 *
 * <p>The default {@code recipients} calls {@code resolve} on the bean itself, past the Spring proxy that opens the
 * transaction annotated on {@code resolve}. Without a transaction of its own the read is refused
 * ({@code TenantContext.setForConnection}, D-57), which is how {@code ReminderRuleRlsIT} failed in CI on the first
 * build. A proxy of a stub audience, with the real annotation reading and a recording transaction manager, shows what
 * a Spring bean would do.
 */
class ReminderAudienceTransactionTest {

    /** An audience written before {@code recipients} existed: {@code resolve} only. */
    static class OlderAudience implements ReminderAudienceResolver {

        @Override
        public String audience() {
            return "OLDER";
        }

        @Override
        public List<UUID> resolve(ReminderRule rule, UUID tenantId) {
            return List.of(UUID.randomUUID());
        }
    }

    @Test
    @DisplayName("The default recipients() opens a read-only transaction around the older audience's resolve()")
    void theDefaultOpensTheReadOnlyTransaction() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus(true));
        OlderAudience target = new OlderAudience();

        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(false);
        factory.addInterface(ReminderAudienceResolver.class);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        ReminderAudienceResolver bean = (ReminderAudienceResolver) factory.getProxy();

        List<ReminderRecipient> recipients = bean.recipients(null, UUID.randomUUID(), LocalDate.of(2026, 10, 2));

        assertThat(recipients).hasSize(1);
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly())
                .as("one read-only transaction, opened by the proxy around the default method")
                .isTrue();
    }
}
