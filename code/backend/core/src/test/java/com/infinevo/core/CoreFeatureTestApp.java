package com.infinevo.core;

import com.infinevo.shared.identity.UserProfileSyncService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The one Spring context the employee and org-master integration tests run in (W-13.1, W-14.1).
 *
 * <p>One class, and it has to be one. Each of these used to have its own
 * {@code @SpringBootApplication} — {@code EmployeeTestApp} and an {@code OrgTestApp} — and the pair
 * could not coexist: the employee context has to scan {@code com.infinevo.core.org} since W-14.1,
 * scanning it found the other {@code @SpringBootApplication} sitting there in test sources, and its
 * {@code @EnableJpaRepositories} registered {@code workLocationRepository} a second time. Spring
 * refused the whole context with {@code BeanDefinitionOverrideException}. Two application classes in
 * two packages that scan each other cannot both exist, so there is one.
 *
 * <p>The two packages are scanned together because the feature genuinely reaches across them. The org
 * services count the employees assigned to a master — that is what makes a delete refusable — and
 * {@code EmployeeServiceImpl} resolves the three masters inside the bound tenant, which is what
 * refuses a cross-tenant assignment. Neither half would start alone.
 *
 * <p>Component scanning is pinned to those two packages rather than to {@code com.infinevo.core},
 * which would drag in the cache and queue beans and their infrastructure. The entity and repository
 * scans are spelled out because {@code scanBasePackages} feeds component scanning only; entity and
 * repository discovery otherwise default to the package this class sits in.
 *
 * <p>The tenant binding filter and the DataSource proxy arrive through
 * {@code TenantBindingAutoConfiguration}, exactly as they do in the real applications, so the tenant
 * reaches the connection the shipped way rather than a test one.
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.core.employee", "com.infinevo.core.org"})
@EntityScan(
        basePackages = {
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            // W-13.4: EmployeeServiceImpl now depends on UserAccountRepository (linkLogin,
            // currentEmployee). UserAccount is a shared identity entity; without this entry
            // Hibernate cannot map it and Spring cannot satisfy the repository autowire.
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            // W-13.4: same reason — UserAccountRepository must be registered.
            "com.infinevo.shared.identity"
        })
@Import(UserProfileSyncService.class)
public class CoreFeatureTestApp {}
