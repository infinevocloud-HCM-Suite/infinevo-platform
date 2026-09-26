package com.infinevo.core.audit;

import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.identity.UserProfileSyncService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The Spring context {@code EmployeeDetailAuditIT} runs in — the employee feature <em>with</em> the
 * audit listener registered (W-13.2, spec section 2).
 *
 * <p>Separate from {@code CoreFeatureTestApp}, and it has to be. The listener is registered while
 * Hibernate builds the {@code SessionFactory}, so it is either on for a whole context or off for it;
 * turning it on inside {@code CoreFeatureTestApp} would make every other core integration test write
 * to {@code core.audit_log} — a table those suites never create — and each would fail from inside a
 * post-commit hook with "relation does not exist", which reads like an outage rather than a test that
 * asked for something it did not set up.
 *
 * <p>It lives in {@code com.infinevo.core.audit} and not beside the test that uses it, for a reason
 * {@code CoreFeatureTestApp} learned the hard way: that class component-scans
 * {@code com.infinevo.core.employee}, so a second {@code @SpringBootApplication} anywhere under it
 * would be found by its scan and its repository registrations would collide.
 *
 * <p>{@link AuditWriter} and {@link AuditIntegratorConfig} are <strong>imported by name rather than
 * component-scanned</strong>. {@code com.infinevo.shared.audit} holds {@code AuditTestApp} in
 * {@code shared}'s test-jar, which is on this module's test classpath; scanning that package would
 * find a second {@code @SpringBootApplication} and recurse into the whole of
 * {@code com.infinevo.shared}.
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.core.employee", "com.infinevo.core.org"})
@EntityScan(
        basePackages = {
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@Import({AuditWriter.class, AuditIntegratorConfig.class, UserProfileSyncService.class})
public class CoreAuditTestApp {}
