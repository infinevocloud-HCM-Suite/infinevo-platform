# Infrastructure PostgreSQL Canonical Scripts

This directory contains the canonical database provisioning scripts for the Infinevo HCM Platform.

## Script Execution Order

1. **`01-roles.sql`**: Parameterized SQL script creating platform database roles (`migration_user`, `app_user`, `readonly_user`) with explicit role attributes (`NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOBYPASSRLS`). Role passwords are passed via `psql` variables (`:'app_pw'`, `:'migration_pw'`, `:'readonly_pw'`).
2. **`02-schemas.sql`**: ANSI/Postgres SQL script creating platform schemas (`core`, `hrms`, `payroll`, `reference`) owned by `migration_user` (`AUTHORIZATION migration_user`). Pure SQL with no `psql` meta-commands.
3. **`03-grants.sql`**: ANSI/Postgres SQL script configuring database connection security, schema usage, default table/sequence privileges (`ALTER DEFAULT PRIVILEGES FOR ROLE migration_user`), and a self-verification assertion `DO` block. Pure SQL with no `psql` meta-commands.
4. **`provision.sh`**: Shell script executing `01-roles.sql`, `02-schemas.sql`, and `03-grants.sql` in sequence using `psql -v ON_ERROR_STOP=1`.

## Consumers

- **Docker Compose (`W-02` / `W-05`)**: Mounted into the Postgres container and executed by `00-bootstrap.sh` on initial stack startup.
- **Testcontainers (`W-04` / `W-05`)**: `02-schemas.sql` and `03-grants.sql` are copied to test classpath by `shared/pom.xml` and executed over JDBC by `PostgresTestContainerInitializer`.
- **Azure Flexible Server (`W-50`)**: Executed post-deployment against Azure Database for PostgreSQL Flexible Server.
