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
  check "schema $s exists" \
    "$C exec -T postgres psql -tAU postgres -d infinevo -c \"select 1 from information_schema.schemata where schema_name='$s'\" | grep -q 1"
done
check "keycloak database exists" \
  "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_database where datname='keycloak'\" | grep -q 1"

echo "── roles and privileges"
for r in app_user migration_user readonly_user; do
  check "role $r exists" \
    "$C exec -T postgres psql -tAU postgres -c \"select 1 from pg_roles where rolname='$r'\" | grep -q 1"
done

# The one that matters. If app_user CAN create a table, the roles are wrong and
# row-level security will not be a boundary when W-07 lands.
echo "── app_user must NOT be able to run DDL"
if $C exec -T postgres psql -U app_user -d infinevo \
     -c "create table core.smoke_should_fail(id int);" >/dev/null 2>&1; then
  bad "app_user was ALLOWED to create a table - roles are wrong"
  $C exec -T postgres psql -U postgres -d infinevo \
     -c "drop table if exists core.smoke_should_fail;" >/dev/null 2>&1
else
  ok "app_user is refused DDL"
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
