-- purge-tenant.sql — delete every row one tenant owns, in one transaction.
--
-- For a tenant created by mistake or a test tenant to be cleared. Not for a tenant with real
-- payroll or audit history that must be kept: cancel that one (PUT .../subscription/status) instead.
--
--   DRY RUN (default — deletes inside a transaction, prints the counts, then rolls back):
--     psql "$MIGRATION_URL" -v tenant_id=<uuid> -v confirm_name='<exact tenant name>' -f purge-tenant.sql
--
--   APPLY (commits):
--     psql "$MIGRATION_URL" -v tenant_id=<uuid> -v confirm_name='<exact tenant name>' -v apply=1 -f purge-tenant.sql
--
-- Run as migration_user, the owner of every table: row-level security does not apply to the owner
-- (no table uses FORCE ROW LEVEL SECURITY), and the REVOKE DELETEs on append-only tables (audit_log,
-- pay_input, document, notification, impersonation_session, …) are revoked from app_user only.
--
-- Every table in core, hrms and payroll carries tenant_id (V001__tenant.sql; CI checks it), so the
-- rows to delete are exactly "WHERE tenant_id = <uuid>". Order is found, not hard-coded: each pass
-- deletes from every table it can; a table still referenced by another of this tenant's rows fails
-- with a foreign-key error and waits for the next pass. No progress in a pass stops the whole run.
--
-- Outside the database, printed for you to finish by hand:
--   * Keycloak users that belong to no other tenant — delete them in the Keycloak admin console.
--   * Stored files under the tenant's prefix in the `documents` blob container.
--   Redis keys (infinevo:<tenant>:…) expire on their own.

\set ON_ERROR_STOP on
\set VERBOSITY terse

\if :{?tenant_id}
\else
  \echo 'Usage: psql ... -v tenant_id=<uuid> -v confirm_name=''<tenant name>'' [-v apply=1] -f purge-tenant.sql'
  \quit
\endif
\if :{?confirm_name}
\else
  \echo 'confirm_name is required: the tenant''s exact name, as a second check on the id.'
  \quit
\endif
\if :{?apply}
\else
  \set apply 0
\endif

SELECT set_config('purge.tenant_id', :'tenant_id', false);
SELECT set_config('purge.confirm_name', :'confirm_name', false);

BEGIN;

DO $purge$
DECLARE
    v_tenant      uuid := current_setting('purge.tenant_id')::uuid;
    v_confirm     text := current_setting('purge.confirm_name');
    v_platform    constant uuid := '00000000-0000-0000-0000-000000000001'; -- V082
    v_name        text;
    v_remaining   text[];
    v_next        text[];
    v_table       text;
    v_rows        bigint;
    v_total       bigint := 0;
    v_pass        int := 0;
    v_last_error  text;
    r             record;
BEGIN
    IF v_tenant = v_platform THEN
        RAISE EXCEPTION 'Refused: % is the Infinevo platform tenant', v_tenant;
    END IF;

    IF NOT pg_has_role(current_user, 'migration_user', 'MEMBER') THEN
        RAISE EXCEPTION 'Run as migration_user (the table owner); connected as %', current_user;
    END IF;

    SELECT name INTO v_name FROM core.tenant WHERE tenant_id = v_tenant;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No tenant %', v_tenant;
    END IF;
    IF v_name IS DISTINCT FROM v_confirm THEN
        RAISE EXCEPTION 'confirm_name does not match: tenant % is named "%"', v_tenant, v_name;
    END IF;

    RAISE NOTICE 'Tenant % "%"', v_tenant, v_name;

    -- What the database cannot clean: report it before the rows that say where it is are gone.
    FOR r IN
        SELECT ua.keycloak_user_id, ua.email
        FROM core.user_account ua
        WHERE ua.tenant_id = v_tenant
          AND NOT EXISTS (
              SELECT 1 FROM core.user_tenant ut
              WHERE ut.user_id = ua.keycloak_user_id AND ut.tenant_id <> v_tenant)
        ORDER BY ua.email
    LOOP
        RAISE NOTICE 'Keycloak user to delete by hand (in no other tenant): % %', r.keycloak_user_id, r.email;
    END LOOP;
    FOR r IN
        SELECT ua.keycloak_user_id, ua.email
        FROM core.user_account ua
        WHERE ua.tenant_id = v_tenant
          AND EXISTS (
              SELECT 1 FROM core.user_tenant ut
              WHERE ut.user_id = ua.keycloak_user_id AND ut.tenant_id <> v_tenant)
        ORDER BY ua.email
    LOOP
        RAISE NOTICE 'Keycloak user to KEEP (also in another tenant): % %', r.keycloak_user_id, r.email;
    END LOOP;
    SELECT count(*) INTO v_rows FROM core.document WHERE tenant_id = v_tenant;
    RAISE NOTICE 'Stored files: % document row(s); blobs under "%/" in each container they name', v_rows, v_tenant;

    -- core.tenant goes last but points at its own logo (V160): let go of it first, or
    -- core.document can never be deleted.
    UPDATE core.tenant SET logo_document_id = NULL WHERE tenant_id = v_tenant;

    -- Every base table outside reference that carries tenant_id; core.tenant itself goes last.
    SELECT array_agg(format('%I.%I', c.table_schema, c.table_name) ORDER BY c.table_schema, c.table_name)
    INTO v_remaining
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
    WHERE c.column_name = 'tenant_id'
      AND c.table_schema IN ('core', 'hrms', 'payroll')
      AND NOT (c.table_schema = 'core' AND c.table_name = 'tenant');

    WHILE coalesce(array_length(v_remaining, 1), 0) > 0 LOOP
        v_pass := v_pass + 1;
        v_next := ARRAY[]::text[];
        FOREACH v_table IN ARRAY v_remaining LOOP
            BEGIN
                EXECUTE format('DELETE FROM %s WHERE tenant_id = $1', v_table) USING v_tenant;
                GET DIAGNOSTICS v_rows = ROW_COUNT;
                v_total := v_total + v_rows;
                IF v_rows > 0 THEN
                    RAISE NOTICE '  pass % %: % row(s)', v_pass, v_table, v_rows;
                END IF;
            EXCEPTION WHEN foreign_key_violation THEN
                v_next := v_next || v_table;
                v_last_error := SQLERRM;
            END;
        END LOOP;
        IF array_length(v_next, 1) = array_length(v_remaining, 1) THEN
            RAISE EXCEPTION 'Stuck after % pass(es); still referenced: % (last error: %)',
                v_pass, array_to_string(v_next, ', '), v_last_error;
        END IF;
        v_remaining := v_next;
    END LOOP;

    DELETE FROM core.tenant WHERE tenant_id = v_tenant;
    v_total := v_total + 1;

    RAISE NOTICE 'Deleted % row(s) in % pass(es), the tenant row included', v_total, v_pass;
END
$purge$;

\if :apply
  COMMIT;
  \echo 'APPLIED: the tenant and all its rows are deleted. Finish the Keycloak users and blobs listed above.'
\else
  ROLLBACK;
  \echo 'DRY RUN: nothing was deleted. Re-run with -v apply=1 to commit.'
\endif
