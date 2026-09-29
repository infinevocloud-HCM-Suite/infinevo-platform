package com.infinevo.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.cache.RedisConfig;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.queue.AzureStorageQueueProducer;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.test.AzuriteTestContainer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The context the notification integration tests share (W-20.1) — one for all three, so the
 * connection budget ({@code W-04.1}) pays for one pool.
 *
 * <p>{@code DocumentTestApp}'s shape with {@code core.notification} in place of the document store,
 * and two stand-ins for what the running application configures:
 *
 * <ul>
 *   <li>a queue producer on Azurite, where {@code StorageQueueConfig} ({@code W-52.1}) would need
 *       {@code azure.storage.queue.connection-string};
 *   <li>a recipient resolver that names the caller's employee directly, in place of {@code W-13.4}'s
 *       login link, so "only the caller's" can be shown without seeding a linked login.
 * </ul>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.notification",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.notification",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.notification",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class NotificationTestApp {

    @Bean
    QueueProducer notificationTestQueueProducer(ObjectMapper objectMapper) {
        return new AzureStorageQueueProducer(AzuriteTestContainer.connectionString(), objectMapper);
    }

    @Bean
    @Primary
    LinkedRecipients linkedRecipients() {
        return new LinkedRecipients();
    }

    /** Login subject to employee id, filled by the test that needs it. */
    static class LinkedRecipients implements NotificationRecipientResolver {

        private final Map<String, UUID> links = new ConcurrentHashMap<>();

        void link(UUID sub, UUID employeeId) {
            links.put(sub.toString(), employeeId);
        }

        @Override
        public Optional<UUID> currentEmployeeId() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth == null ? Optional.empty() : Optional.ofNullable(links.get(auth.getName()));
        }
    }
}
