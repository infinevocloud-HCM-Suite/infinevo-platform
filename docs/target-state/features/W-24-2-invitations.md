# Feature: User and employee invitations

| Field | Value |
|---|---|
| **Feature ID** | `W-24.2` · from ticket #28 · `CORE-18` |
| **Promoted to** | `docs/target-state/features/W-24-2-invitations.md` on branch `W-24-2-invitations` — **`W-24-2` with hyphens**, never `W-24.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-004 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-10` (Keycloak), `W-11.1` (a role to grant), `W-20.1` (the invitation email) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 19 (§1 Setup row, §2 `/user-invitations` row) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, **three tables** — `user_invitation` and its join table `user_invitation_role` share a script; `employee_invitation` has its own — aggregate exception | 1 — exception granted 2026-09-22, extended 2026-09-25 for the join table |
| Externally testable behaviour | an invitation is sent, accepted once, and grants exactly the intended access | 1 |
| Frontend area | none | 1 |

Within cap. The two invitation tables are one aggregate with one lifecycle; the join table
has no life of its own and rides in the `user_invitation` script, the same shape as `W-17`'s
exception (`12-core-contracts.md` §5 row 9).

---

## 1. Problem

Two invitation types exist with different rules, and the one with more power has weaker
controls.

- `userInvitations` carries a token and **no expiry column at all** — a company-user invitation is valid forever
- `employeeInvitation` carries a token **and** an expiry, seven days
- So the invitation that grants administrative access never expires, and the one that grants an employee their own payslips does
- **Resend is delete-and-recreate.** There is no resend path, so the old token's fate depends on the delete succeeding
- **Revoke is a hard delete** for users, which removes the record that anyone was ever invited

The acceptance flow also creates the Keycloak user **at invitation time**, not at acceptance:
`UserInvitationServiceImpl.java:139` creates a temporary Keycloak user, and acceptance later
assigns the role at `InvitationAcceptanceController.java:226`. An invitation that is never
accepted leaves a real, enabled account behind in the realm.

This ticket also closes the gap `W-10` deliberately left: a user with no `core.user_tenant`
row gets `401`, and nothing could create that row. This is what creates it.

## 2. Scope

**In scope**

- `core.user_invitation`, `core.user_invitation_role` and `core.employee_invitation`
- One lifecycle for both: `PENDING`, `ACCEPTED`, `DECLINED` (with a reason), `REVOKED`, `EXPIRED`
- List, resend and revoke for **both** invitation types, not only user invitations
- **Expiry on both, seven days** — settled 2026-09-22
- A single-use token, stored hashed
- Acceptance creating the Keycloak user, the `core.user_account` row and the `core.user_tenant` row
- Resend as a first-class action that invalidates the previous token
- Revoke as a state, not a deletion

**Out of scope**

- The setup checklist — `W-24.1`
- Bulk employee invitation from a file — `W-13` owns employee import if it is ever needed
- The acceptance screens
- Self-service signup. `W-12.1` decision 3 keeps tenant creation with `platform-admin`

## 3. Flow

```
[tenant admin] --> POST /api/v1/user-invitations {email, roleIds}
   --> token generated, hash stored, plaintext sent once
   --> [W-20.1 compose INVITATION] --> [W-20.2 deliver]

[invitee] --> POST /api/v1/invitations/accept {token}
   --> hash matches, not expired, not used
   --> Keycloak user created --> core.user_account --> core.user_tenant --> core.user_role
   --> invitation ACCEPTED

[invitee] --> POST /api/v1/invitations/decline {token, reason}
   --> hash matches, not expired, not used --> invitation DECLINED, reason stored, nothing provisioned
```

**Decline is kept from the frozen flow.** Both legacy acceptance paths take `REJECT` with a
`rejectionReason` — `InvitationAcceptanceController.java:211-216` for users and `:250-254`
for employees — and a declined invitation is a different fact from an expired one: the
person said no. It ends the token exactly as acceptance does.

**Keycloak user created at acceptance, not at invitation.** That is the change from the
frozen flow, and it is what stops unaccepted invitations leaving live accounts behind.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../invitation/UserInvitationController.java`, `EmployeeInvitationController.java` | new |
| Controller | `core/.../invitation/InvitationAcceptanceController.java` | new — unauthenticated, token only |
| Service | `core/.../invitation/InvitationService.java` | new |
| Service | `core/.../invitation/KeycloakProvisioningService.java` | new — admin-API user creation |
| Entity | `core/.../invitation/UserInvitation.java`, `EmployeeInvitation.java` | new, each `@Table(schema="core")` |
| Entity | `core/.../invitation/UserInvitationRole.java` | new — one row per `(invitation, role)`, `@Table(schema="core")` |
| Repository | three | new |
| Enumeration | `core/.../invitation/InvitationStatus.java` | new — `PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED` |

**The acceptance endpoint is unauthenticated by necessity** — the invitee has no account yet.
It must therefore be added to `TenantContextFilter`'s exempt list, which today holds
`/actuator/health`, the API-doc paths and `/api/v1/auth/login` — and `W-10` removes that last
one. This adds `/api/v1/invitations/accept` and `/api/v1/invitations/decline` in its place,
and those are the only unauthenticated write endpoints in the platform. Both take the token
and nothing else identifies the invitation.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/user-invitations` | email, roleIds | `201` | `@RequiresAction("core.user.manage")` |
| GET | `/api/v1/user-invitations` | `?status=` | list, never the token | `core.user.manage` |
| POST | `/api/v1/user-invitations/{id}/resend` | — | `200`, new token, old invalid | `core.user.manage` |
| POST | `/api/v1/user-invitations/{id}/revoke` | — | `200` | `core.user.manage` |
| POST | `/api/v1/employee-invitations` | employeeId | `201` | `@RequiresAction("core.employee.create")` |
| GET | `/api/v1/employee-invitations` | `?status=&employeeId=` | list, never the token | `core.employee.create` |
| POST | `/api/v1/employee-invitations/{id}/resend` | — | `200`, new token, old invalid | `core.employee.create` |
| POST | `/api/v1/employee-invitations/{id}/revoke` | — | `200` | `core.employee.create` |
| POST | `/api/v1/invitations/accept` | token | `200` | **none** — the token is the credential |
| POST | `/api/v1/invitations/decline` | token, reason (≤ 500) | `200` | **none** — the token is the credential |

Both codes exist in the catalogue: `core.user.manage` — "Invite, update and deactivate user
accounts" — at `reference/V020__action.sql:50`, `core.employee.create` at `:61`. Inviting an
employee is part of creating one, so it takes no new code.

**Employee invitations get the same four verbs as user invitations.** The frozen employee
controller already lists (`EmployeeInvitationController.java:50-60`), fetches one (`:62-73`)
and updates in place (`:36-48`); there is no resend or revoke there either, so today an
expired employee invitation is fixed by editing the row. One lifecycle means one set of verbs.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__user_invitation.sql` | `core.user_invitation`, `core.user_invitation_role` | yes | additive |
| `core/V0NN__employee_invitation.sql` | `core.employee_invitation` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`user_invitation`: `id uuid` · `tenant_id uuid NOT NULL` · `email varchar(255) NOT NULL` ·
`token_hash char(64) NOT NULL` ·
`status varchar(16) NOT NULL` · `expires_at timestamptz NOT NULL` ·
`accepted_at timestamptz NULL` · `declined_at timestamptz NULL` ·
`decline_reason varchar(500) NULL` · `revoked_at timestamptz NULL` ·
`superseded_by_id uuid NULL REFERENCES core.user_invitation(id)` ·
`invited_by_user_id uuid NOT NULL` · four audit columns.

`user_invitation_role`: `tenant_id uuid NOT NULL` ·
`invitation_id uuid NOT NULL REFERENCES core.user_invitation(id)` ·
`role_id uuid NOT NULL REFERENCES core.role(id)` · unique
`(tenant_id, invitation_id, role_id)`, as `V023__user_role.sql:23` does. **Replaces the `role_ids text` column** the first draft
carried (`12-core-contracts.md` §5 row 19): a delimited string cannot be joined, cannot be
foreign-keyed to `core.role`, and lets a deleted role be granted at acceptance. The row shape
is `core.user_role`'s (`V023`) with `invitation_id` in place of `user_account_id`, and
acceptance copies rows across, one to one.

`employee_invitation`: as `user_invitation`, with `employee_id uuid NOT NULL REFERENCES
core.employee(id)` in place of the role join — the employee's role is the seeded `employee`
role, not a choice.

- [x] `tenant_id` on all three, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, email, status)` on both invitation tables, `(tenant_id, employee_id)` on `employee_invitation`, the primary key on the join table, plus a **global** unique index on `token_hash` (see below)
- [x] Money columns — none
- [x] Expand / contract — new tables only

**The token is stored hashed, never in plaintext.** The frozen tables store the token itself,
so anyone who can read the table can accept any invitation. SHA-256 of a 256-bit random value
is enough; this is a single-use bearer token with a short life, not a password.

**The token-hash index cannot be tenant-scoped**, because acceptance happens before any tenant
is bound — the lookup is by token alone. This is the one query in the platform that runs
outside a tenant context, and it therefore needs its own narrow route: a `SECURITY DEFINER`
function, like `core.get_user_tenants` in `V002__user_tenant.sql:30-40`, granted to
`app_user` and returning only the invitation matching an exact hash. That pattern already
exists in the codebase for exactly this shape of problem.

**Revoked, declined and superseded rows are kept.** `superseded_by_id` links a resend to what
it replaced, so "we invited them three times and they never accepted" is answerable, and
`decline_reason` keeps what the frozen `markAsRejected` kept
(`InvitationAcceptanceController.java:212-215`).

RLS and the `tenant_isolation` policy in the exact `CASE` form on all three tables, in each
script — `migration/README.md:76-123`. The join table is under RLS in its own right, not by
inheritance through the FK.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../invitation/InvitationServiceTest.java` | token is single-use; an expired token refused; a revoked one refused; resend invalidates the previous |
| Unit | `core/.../invitation/TokenHashingTest.java` | the plaintext token is never persisted and never logged |
| Integration | `core/.../invitation/AcceptanceIT.java` | acceptance creates the Keycloak user, `user_account`, `user_tenant` and `user_role` — and a second acceptance with the same token is refused |
| Integration | `core/.../invitation/NoOrphanKeycloakUserIT.java` | **an invitation that expires unaccepted leaves no Keycloak user** |
| Integration | `core/.../invitation/InvitationRlsIT.java` | tenant A cannot list or revoke tenant B's invitations, nor read its `user_invitation_role` rows; the lookup function returns only the exact-hash match |
| Unit | `core/.../invitation/InvitationDeclineTest.java` | decline stores the reason and ends the token; a declined token cannot then be accepted; decline of an expired token is refused; a blank reason is refused |
| Integration | `core/.../invitation/RoleJoinAcceptanceIT.java` | an invitation with two roles produces exactly two `core.user_role` rows on acceptance; an invitation naming a role that no longer exists is refused at **create**, not at acceptance |
| Integration | `core/.../invitation/EmployeeInvitationLifecycleIT.java` | list filters by status and employee; resend supersedes and invalidates; revoke is a state and the old token is refused |
| Integration | `core/.../invitation/InvitationGuardIT.java` | user endpoints refuse without `core.user.manage`; employee endpoints refuse without `core.employee.create`; holding one does not grant the other |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`, and `AcceptanceIT`
adds a Keycloak container as `W-10`'s `LoginFlowIT` does.

`NoOrphanKeycloakUserIT` asserts the specific change from `UserInvitationServiceImpl.java:139`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml up --build migrate
for t in user_invitation user_invitation_role employee_invitation; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT column_name FROM information_schema.columns
      WHERE table_schema='core' AND table_name='$t' AND column_name IN ('token','expires_at','role_ids');"
done
cd code/backend && mvn -q -pl core -Dit.test=AcceptanceIT,NoOrphanKeycloakUserIT verify
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all three | `t` three times |
| Columns | `expires_at` present on **both** invitation tables; no column named `token` or `role_ids` anywhere |
| Acceptance test | green — one acceptance, second refused |
| No-orphan test | green |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| An administrative invitation never expires, as today | **medium — it is the frozen behaviour** | `expires_at` is `NOT NULL` on both tables; verification checks the column exists |
| The token is stored or logged in plaintext | medium, severe | Hashed column, `TokenHashingTest`, and the same no-logging discipline as `W-21` |
| The unauthenticated acceptance endpoint is abused to enumerate invitations | medium | Exact-hash lookup only, through a narrow `SECURITY DEFINER` function; no listing, no partial match, and a generic failure response |
| Accepting twice creates two accounts | medium | Single-use enforced in the accepting transaction, asserted by test |
| An unaccepted invitation leaves a live Keycloak account (`UserInvitationServiceImpl.java:139`) | **medium — it is today's behaviour** | Provisioning moved to acceptance; `NoOrphanKeycloakUserIT` |
| Resend leaves two valid tokens | medium | `superseded_by_id` and an explicit invalidation, unit-tested |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`. Revoking outstanding invitations is a state change, not a
deletion.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all three tables; the join table shares `user_invitation`'s script under the size-cap exception |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | tenant-scoped indexes on both, plus the one deliberate global index on `token_hash` |
| Expand / contract | new tables only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| `userInvitations` has no expiry | **Fixed.** `expires_at NOT NULL` on both |
| Tokens stored in plaintext | **Fixed.** Hashed, single-use |
| Resend is delete-and-recreate | **Fixed.** A first-class action with supersession recorded |
| Revoke is a hard delete | **Fixed.** A state |
| Rejection with a reason (`InvitationAcceptanceController.java:211-216, 250-254`) | **Kept** as `DECLINED` + `decline_reason`, token-authenticated like acceptance |
| Employee invitations have list and edit but no resend or revoke (`EmployeeInvitationController.java:36-60`) | **Fixed.** Same four verbs as user invitations |
| Keycloak user created at invitation (`UserInvitationServiceImpl.java:139`) | **Fixed.** Created at acceptance |
| `W-10`'s deferred gap — nobody can be provisioned | **Fixed.** This is the ticket that closes it |
| DEBT-004 secrets in `.properties` | **Discounted.** The Keycloak admin credential comes from Key Vault via `W-56` |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | Invitation expiry | **Seven days for both types.** Against my recommendation of three days for company users |
| 2 | An invited email already has an account in another tenant | **Reuse the account**, adding a `user_tenant` row rather than creating a second Keycloak user |
| 3 | Does revoking disable an already-accepted user? | **No.** Revoke applies to pending invitations only; removing active access is a `W-11.1` role change |

**Decision 1** keeps one rule rather than two, and matches the frozen employee behaviour. The
trade accepted: an administrative invitation — the more powerful token — stays live as long as
an employee's. Resend now invalidates the previous token, so shortening it later is a
configuration change rather than a redesign.

**Decision 2** means one credential can reach two customers' data. That is what
`core.user_tenant` exists for, and `W-08`'s binding filter already verifies membership per
request, so the isolation still holds — but it is worth knowing that a compromised consultant
account is a two-customer incident, not a one-customer one.
