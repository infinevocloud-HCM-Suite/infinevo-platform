#!/usr/bin/env bash
# Proves the local stack actually works. Run after `up -d`.
#
#   infra/docker/smoke.sh
#
# Exit 0 = every check passed. Exit 1 = at least one failed, and it says which.
set -uo pipefail

C="docker compose -f $(dirname "$0")/compose.yml"
pass=0; fail=0

ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL  $1"; fail=$((fail+1)); }
check(){ if eval "$2" >/dev/null 2>&1; then ok "$1"; else bad "$1"; fi; }

echo "── containers"
for s in postgres redis queue blob mail keycloak app worker web; do
  check "$s is running" "$C ps --status running --services | grep -qx $s"
done

echo "── endpoints"
check "app health is UP"      "curl -fsS http://localhost:${APP_PORT:-8080}/actuator/health | grep -q UP"
check "worker health is UP"   "curl -fsS http://localhost:${WORKER_PORT:-8082}/actuator/health | grep -q UP"
check "web responds"          "curl -fsS http://localhost:${WEB_PORT:-5173}"
check "keycloak realm exists" "curl -fsS http://localhost:${KEYCLOAK_PORT:-8081}/realms/infinevo"
# The README documents these admin credentials, so prove they work. The realm importing
# is independent of the admin user existing - checking only the realm hid a broken login.
check "keycloak admin can sign in"   "curl -fsS -X POST http://localhost:${KEYCLOAK_PORT:-8081}/realms/master/protocol/openid-connect/token -d client_id=admin-cli -d username=admin -d password=local_keycloak_pw -d grant_type=password"
check "seeded users can sign in"   "curl -fsS -X POST http://localhost:${KEYCLOAK_PORT:-8081}/realms/infinevo/protocol/openid-connect/token -d client_id=infinevo-web -d username=admin.acme -d password=local_dev_pw -d grant_type=password"
check "mail catcher is up"    "curl -fsS http://localhost:${MAIL_UI_PORT:-8025}"

echo "── database"
for s in core hrms payroll reference; do
  check "schema $s exists and owned by migration_user" \
    "$C exec -T postgres psql -tAU postgres -d infinevo -c \"select pg_get_userbyid(nspowner) from pg_namespace where nspname='$s'\" | grep -qx migration_user"
done
check "schema migration exists and owned by migration_user" \
  "$C exec -T postgres psql -tAU postgres -d infinevo -c \"select pg_get_userbyid(nspowner) from pg_namespace where nspname='migration'\" | grep -qx migration_user"
check "flyway_schema_history exists in migration schema owned by migration_user" \
  "$C exec -T postgres psql -tAU postgres -d infinevo -c \"select count(*)||'|'||schemaname||'/'||tableowner from pg_tables where tablename='flyway_schema_history' group by schemaname, tableowner\" | grep -qx '1|migration/migration_user'"
check "app_user has no USAGE on migration schema" \
  "$C exec -T postgres psql -tAU postgres -d infinevo -c \"select has_schema_privilege('app_user','migration','USAGE')\" | grep -qx f"
check "keycloak database exists" \
  "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_database where datname='keycloak'\" | grep -q 1"
check "keycloak database owned by keycloak_user" \
  "$C exec -T postgres psql -tAU postgres -c \"select pg_get_userbyid(datdba) from pg_database where datname='keycloak'\" | grep -qx keycloak_user"

echo "── roles and privileges"
# 1. worker_user inherits from app_user. Read from the catalogue rather than inferred from
# a query succeeding: a grant that is missing but compensated elsewhere would still pass a
# behavioural test, and then break the moment the compensation moves.
check "worker_user inherits from app_user" \
  "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_auth_members m join pg_roles r on r.oid=m.roleid join pg_roles g on g.oid=m.member where r.rolname='app_user' and g.rolname='worker_user'\" | grep -q 1"

# 2. Declared attributes, for the new role as well as the four original ones.
for r in app_user worker_user migration_user readonly_user keycloak_user; do
  check "role $r exists" \
    "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_roles where rolname='$r'\" | grep -q 1"
  check "role $r has declared attributes (nosuper, nobypassrls, nocreatedb, nocreaterole)" \
    "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_roles where rolname='$r' and rolsuper=false and rolbypassrls=false and rolcreatedb=false and rolcreaterole=false\" | grep -q 1"
done

# F-7: ask Postgres the question directly. The old form grepped datacl for
# '=c/', which also matches "app_user=c/postgres", and grep -v on empty
# input exits 1 - so it reported FAIL on a correct system.
check "PUBLIC has no CONNECT on infinevo" \
  "$C exec -T postgres psql -tAU postgres -c \"select has_database_privilege('public','infinevo','CONNECT')\" | grep -qx f"

# F-8: the old form treated ANY nonzero psql exit as proof of refusal - a
# password prompt or a nonexistent role passed it just as well. Asking
# has_database_privilege fails loudly if the role is absent.
check "readonly_user refused connection on keycloak db" \
  "$C exec -T postgres psql -tAU postgres -c \"select has_database_privilege('readonly_user','keycloak','CONNECT')\" | grep -qx f"

# The one that matters. If app_user CAN create a table, the roles are wrong and
# row-level security will not be a boundary when W-07 lands.
echo "── app_user must NOT be able to run DDL"
ddl_output=$($C exec -T postgres psql -U app_user -d infinevo \
     -c "create table core.smoke_should_fail(id int);" 2>&1 || true)
if echo "$ddl_output" | grep -q "permission denied for schema core"; then
  ok "app_user is refused DDL (permission denied for schema core)"
else
  bad "app_user DDL check failed (expected permission denied): $ddl_output"
  $C exec -T postgres psql -U postgres -d infinevo \
     -c "drop table if exists core.smoke_should_fail;" >/dev/null 2>&1 || true
fi

echo "── configuration"
# Match an actual SETTING, not the comments that say there must never be one.
# A real setting is "ddl-auto:" or "ddl-auto=" on a line that is not a comment.
if grep -rEn '^[^#]*ddl-auto[[:space:]]*[:=]' "$(dirname "$0")/../../code"      --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | grep -q .; then
  bad "ddl-auto is SET in a configuration file"
  grep -rEn '^[^#]*ddl-auto[[:space:]]*[:=]' "$(dirname "$0")/../../code"      --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | sed 's/^/        /'
else
  ok "ddl-auto is set nowhere"
fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
