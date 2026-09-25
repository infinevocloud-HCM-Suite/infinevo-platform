package com.infinevo.core.document;

import com.infinevo.shared.cache.RedisConfig;
import com.infinevo.shared.identity.UserProfileSyncService;
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
 * The context the document store's integration tests share (W-21) — one context for all three, so
 * the connection budget ({@code W-04.1}) pays for one pool rather than three.
 *
 * <p>{@code PermissionGuardTestApp}'s shape plus {@code com.infinevo.core.document}: the permission
 * check needs {@code core.authz} for its {@code ActionSource} and {@code shared.identity} for the
 * profile lookup; the store needs {@code core.employee}, whose entity maps the {@code core.org}
 * masters.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.document",
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
            "com.infinevo.core.document",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.document",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class DocumentTestApp {

    /**
     * Stands in for {@code W-13.4}'s login-to-employee link, so {@code read_own} can be shown to admit
     * the caller's own document and nothing else. Without it every {@code read_own} caller is nobody.
     */
    @Bean
    @Primary
    LinkedOwners linkedOwners() {
        return new LinkedOwners();
    }

    /** Login subject to employee id, filled by the test that needs it. */
    static class LinkedOwners implements DocumentOwnerResolver {

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
