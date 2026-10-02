package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * A document that is not there must not poison the caller's transaction (W-34.1 review).
 *
 * <p>{@code get}, {@code open} and {@code delete} are transactional and join the caller's transaction. Spring
 * marks a joined transaction rollback-only when a {@code RuntimeException} leaves such a method, even when
 * the caller catches it, so a caller that treats {@link DocumentService.NotFoundException} as "already gone"
 * (the proof service does, for a file soft-deleted through core) would still have its request fail at commit.
 * The three methods therefore name it in {@code noRollbackFor}; this asks Spring itself how it will treat it.
 */
class DocumentNotFoundTransactionTest {

    private static final AnnotationTransactionAttributeSource SOURCE = new AnnotationTransactionAttributeSource();

    @Test
    @DisplayName("NotFound from get, open or delete does not mark the joined transaction rollback-only")
    void notFoundDoesNotRollBack() throws Exception {
        var notFound = new DocumentService.NotFoundException(UUID.randomUUID());
        for (String name : new String[] {"get", "open", "delete"}) {
            Method method = DocumentServiceImpl.class.getMethod(name, UUID.class);
            TransactionAttribute attribute = SOURCE.getTransactionAttribute(method, DocumentServiceImpl.class);
            assertThat(attribute).as(name + " is transactional").isNotNull();
            assertThat(attribute.rollbackOn(notFound))
                    .as(name + " leaves the caller's transaction alone for a missing document")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Any other failure still rolls back")
    void otherFailuresStillRollBack() throws Exception {
        for (String name : new String[] {"get", "open", "delete"}) {
            Method method = DocumentServiceImpl.class.getMethod(name, UUID.class);
            TransactionAttribute attribute = SOURCE.getTransactionAttribute(method, DocumentServiceImpl.class);
            assertThat(attribute.rollbackOn(new IllegalStateException("boom")))
                    .as(name)
                    .isTrue();
        }
    }
}
