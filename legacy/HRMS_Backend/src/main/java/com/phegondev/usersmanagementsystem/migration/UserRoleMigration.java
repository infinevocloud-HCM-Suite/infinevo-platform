package com.phegondev.usersmanagementsystem.migration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.RoleRepo;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;

import java.util.List;

@Component
public class UserRoleMigration {

    private static final String MIGRATION_KEY = "USER_ROLE_COLUMN_TO_TABLE_MIGRATION_V1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsersRepo usersRepo;

    @Autowired
    private RoleRepo roleRepo;

    @PostConstruct
    public void migrateUserRoles() {
        try {
            System.out.println("⚙️ Checking migration audit for UserRoleMigration...");

            // 1️⃣ Ensure audit table exists
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS migration_audit (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "migration_key VARCHAR(255) UNIQUE NOT NULL, " +
                "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // 2️⃣ Skip if already executed
            Integer alreadyRun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_audit WHERE migration_key = ?",
                Integer.class, MIGRATION_KEY
            );
            if (alreadyRun != null && alreadyRun > 0) {
                System.out.println("✅ Migration '" + MIGRATION_KEY + "' already executed — skipping.");
                return;
            }

            // 3️⃣ Check if column 'role' exists in 'ourusers'
            String checkColumn = """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'ourusers' AND column_name = 'role'
            """;
            Integer columnCount = jdbcTemplate.queryForObject(checkColumn, Integer.class);

            if (columnCount == null || columnCount == 0) {
                System.out.println("ℹ️ Column 'role' does not exist. Migration not required.");
                jdbcTemplate.update("INSERT INTO migration_audit (migration_key) VALUES (?)", MIGRATION_KEY);
                return;
            }

            System.out.println("🚀 Starting migration of user roles from 'ourusers.role' → 'user_roles'...");

            List<OurUsers> users = usersRepo.findAll();
            int migratedCount = 0;
            int skippedCount = 0;

            for (OurUsers user : users) {
                String roleName;
                try {
                    roleName = jdbcTemplate.queryForObject(
                        "SELECT role FROM ourusers WHERE id = ?",
                        new Object[]{user.getId()},
                        String.class
                    );
                } catch (Exception e) {
                    System.out.println("⚠️ Skipping user " + user.getId() + " — cannot read 'role' column.");
                    skippedCount++;
                    continue;
                }

                if (roleName == null || roleName.isBlank()) {
                    skippedCount++;
                    continue;
                }

                Role role = roleRepo.findByRoleName(roleName.toLowerCase().trim());
                if (role == null) {
                    System.out.println("⚠️ Role not found in 'role' table: " + roleName);
                    skippedCount++;
                    continue;
                }

                String insertSql = """
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT ?, ? FROM DUAL
                    WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = ? AND role_id = ?)
                """;
                jdbcTemplate.update(insertSql, user.getId(), role.getRoleId(), user.getId(), role.getRoleId());
                migratedCount++;
            }

            System.out.println("✅ User role migration completed. Migrated: " + migratedCount + " users, Skipped: " + skippedCount);

            // 4️⃣ Drop old column safely
            try {
                jdbcTemplate.execute("ALTER TABLE ourusers DROP COLUMN role");
                System.out.println("🧹 Dropped old column 'role' successfully.");
            } catch (Exception e) {
                System.out.println("ℹ️ Could not drop 'role' column (maybe already deleted): " + e.getMessage());
            }

            // 5️⃣ Record migration
            jdbcTemplate.update("INSERT INTO migration_audit (migration_key) VALUES (?)", MIGRATION_KEY);
            System.out.println("🎯 Migration recorded under key: " + MIGRATION_KEY);

        } catch (Exception e) {
            System.err.println("❌ Error during UserRoleMigration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
