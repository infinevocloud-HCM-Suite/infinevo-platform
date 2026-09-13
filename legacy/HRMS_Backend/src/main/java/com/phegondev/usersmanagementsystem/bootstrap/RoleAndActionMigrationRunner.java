package com.phegondev.usersmanagementsystem.bootstrap;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.UserActionMappingRepository;
import com.phegondev.usersmanagementsystem.service.useraccess.ActionService;
import com.phegondev.usersmanagementsystem.service.useraccess.RoleService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time migration runner for synchronizing roles, actions, and user mappings.
 * Automatically creates migration_audit table if missing.
 */
@Component
public class RoleAndActionMigrationRunner implements CommandLineRunner {

    private final UsersRepo usersRepo;
    private final UserActionMappingRepository userActionMappingRepo;
    private final RoleService roleService;
    private final ActionService actionService;
    private final JdbcTemplate jdbcTemplate;

    private static final String MIGRATION_KEY = "ROLE_ACTION_FULL_MIGRATION_V1";

    public RoleAndActionMigrationRunner(
            UsersRepo usersRepo,
            UserActionMappingRepository userActionMappingRepo,
            RoleService roleService,
            ActionService actionService,
            JdbcTemplate jdbcTemplate
    ) {
        this.usersRepo = usersRepo;
        this.userActionMappingRepo = userActionMappingRepo;
        this.roleService = roleService;
        this.actionService = actionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            System.out.println("⚙️ Starting Role & Action Migration Runner...");

            // 1️⃣ Ensure audit table exists
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS migration_audit (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "migration_key VARCHAR(255) UNIQUE NOT NULL, " +
                "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // 2️⃣ Check if migration was already executed
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_audit WHERE migration_key = ?",
                Integer.class, MIGRATION_KEY
            );
            if (exists != null && exists > 0) {
                System.out.println("✅ Migration '" + MIGRATION_KEY + "' already executed — skipping.");
                return;
            }

            // 3️⃣ Rebuild Role & Action Mappings
            System.out.println("🔁 Creating default roles and actions...");
            actionService.createActionIfNotExist();
            roleService.createRoleIfNotExist();
            System.out.println("🔁 Mapping default actions to roles...");
            actionService.mapDefaultActionsToRoles();

            // 4️⃣ Rebuild User Action Mappings
            System.out.println("🔁 Rebuilding user_action_mapping table...");
            userActionMappingRepo.deleteAll();
            List<OurUsers> users = usersRepo.findAll();

            int processed = 0;
            for (OurUsers user : users) {
                actionService.mapDefaultActionsToUser(user);
                processed++;
            }

            System.out.println("✅ user_action_mapping rebuilt for " + processed + " users.");

            // 5️⃣ Record migration as executed
            jdbcTemplate.update(
                "INSERT INTO migration_audit (migration_key) VALUES (?)",
                MIGRATION_KEY
            );

            System.out.println("🎯 Migration completed successfully and logged under key: " + MIGRATION_KEY);

        } catch (Exception e) {
            System.err.println("❌ Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
