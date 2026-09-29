package com.infinevo.core.invitation;

import com.infinevo.shared.identity.UserProfileSyncService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot context for invitation integration tests (W-24.2).
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.tenant"
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
@Import(UserProfileSyncService.class)
public class InvitationTestApp {}
