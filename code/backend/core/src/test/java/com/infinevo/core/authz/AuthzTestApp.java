package com.infinevo.core.authz;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The Spring context {@link RoleRlsIT} and {@link ActionCatalogueIT} run in (W-11.1).
 *
 * <p>Separate from {@code CoreFeatureTestApp} because it points at a separate database —
 * {@link AuthzTestSchema} explains why. Nothing else in the module scans
 * {@code com.infinevo.core.authz}, so this second {@code @SpringBootApplication} cannot be found by
 * another context's component scan, which is the collision {@code CoreFeatureTestApp} documents.
 *
 * <p>{@code com.infinevo.shared.identity} is added to the entity and repository scans — and only
 * those, not to component scanning — because {@code RoleServiceImpl} reads {@code UserAccount}
 * through {@code UserAccountRepository} to refuse a grant to another tenant's user.
 */
@SpringBootApplication(scanBasePackages = "com.infinevo.core.authz")
@EntityScan(basePackages = {"com.infinevo.core.authz", "com.infinevo.shared.identity"})
@EnableJpaRepositories(basePackages = {"com.infinevo.core.authz", "com.infinevo.shared.identity"})
public class AuthzTestApp {}
