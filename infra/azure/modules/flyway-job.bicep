// ─────────────────────────────────────────────────────────────────────────────
// flyway-job.bicep — caj-flyway-{env} (W-54, closes #138).
//
// A manual-trigger Container Apps job that runs the BACKEND image with
// INFINEVO_ROLE=migration. The entrypoint launches /app/migration.jar
// (infra/docker/backend-entrypoint.sh:22-27), Flyway applies every versioned script, the
// Spring context closes and the JVM exits — web-application-type is none
// (code/backend/migration/src/main/resources/application.yml:3).
//
// IT IS A SIBLING OF caj-db-migration-{env}, NOT A REPLACEMENT (W-54 section 2, "Out of
// scope"). That job provisions roles and runs the six private-path probes as
// infinevo_admin (db-migration-job.bicep:74). This one does Flyway ONLY, and does it as
// migration_user, which is what 05-azure-architecture.md:95,127 requires and what #138
// found missing: the old job applied no migrations at all and reported success for it.
//
// WHY THE BACKEND IMAGE AND NOT migration-runner. migration-runner.Dockerfile:23 is an
// Azure CLI base with no JRE, so it cannot run a jar. The backend image carries
// migration.jar beside app.jar and worker.jar from ONE reactor build
// (infra/docker/backend.Dockerfile:52-57,72-74), so the schema Flyway applies and the code
// that reads it are always the same commit. That is the whole point of the third role.
//
// PLACEMENT ON snet-cae IS INHERITED, NOT SET HERE — same as db-migration-job.bicep. A
// Container Apps job has no subnet property; it runs in the infrastructure subnet of its
// managed environment, which containerapp-env.bicep pins to snet-cae. Without that, the
// psql-infinevo-{env} FQDN resolves to a public address that publicNetworkAccess:
// 'Disabled' refuses, and the failure looks like a credential fault.
//
// NO SECRET VALUE IS PASSED IN, AND NO SECRET IS CREATED. psql-migration-pw is defined by
// W-56 (W-56-secrets.md, secret inventory). W-56 scopes delivery to the four long-running
// apps and caj-db-migration-{env}; this job is newer than that list and W-56 is approved,
// so W-54 wires the READER here using the mechanism W-56 already provisions: the job's own
// user-assigned identity plus Key Vault Secrets User on kv-infinevo-shared, referenced by
// keyVaultUrl + identity. The name of the secret appears below; its value never does.
// ─────────────────────────────────────────────────────────────────────────────

@description('Target environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Resource id of the VNet-injected Container Apps environment (cae-infinevo-{env})')
param environmentId string

@description('ACR login server the backend image is pulled from (e.g. crinfinevo.azurecr.io)')
param acrLoginServer string = 'crinfinevo.azurecr.io'

@description('Repository of the backend image built from infra/docker/backend.Dockerfile')
param backendImageRepository string = 'infinevo-backend'

// NO DEFAULT, deliberately (review finding F-17). db-migration-job.bicep:35 hardcodes
// `:latest`, which means a run applies whatever was pushed last rather than the commit
// being deployed - a silently wrong migration is the worst failure this job has. The
// pipeline passes the immutable git-<sha> tag it built, and the same tag then runs on all
// four apps (W-54 section 5 check 12).
@description('Immutable tag of the backend image to run, e.g. git-1a2b3c4. Required - the pipeline supplies the tag it built.')
param backendImageTag string

@description('Resource id of id-migration-{env} - pulls the image and reads psql-migration-pw from Key Vault')
param migrationIdentityId string

@description('Client id of id-migration-{env}')
param migrationIdentityClientId string

@description('Name of the shared Key Vault holding psql-migration-pw (created by W-56, not by this ticket)')
param keyVaultName string = 'kv-infinevo-shared'

@description('Fully qualified domain name of the PostgreSQL flexible server. Resolves to a 10.x address inside the VNet via privatelink.postgres.database.azure.com.')
param postgresFqdn string

// NOT infinevo_admin. db-migration-job.bicep:74 runs as the server administrator because
// it creates roles and databases; this job must not. 05-azure-architecture.md:95,127 puts
// schema change under migration_user, and section 4 break 6 of W-54 deliberately points
// this job at infinevo_admin to prove check 10 catches it on installed_by.
@description('PostgreSQL login Flyway connects as. Owns the schema; never the server administrator.')
param migrationUsername string = 'migration_user'

@description('PostgreSQL database the migrations are applied to')
param postgresDatabase string = 'infinevo'

@description('Name of the Key Vault secret holding the migration_user password. Defined by W-56; this job only reads it.')
param migrationPasswordSecretName string = 'psql-migration-pw'

@description('Tags for the resource')
param tags object = {}

// sslmode=require, not a default. Azure Database for PostgreSQL Flexible Server rejects a
// cleartext connection, and the JDBC driver does not infer TLS from the host - without
// this the job fails at connect with an error that reads like a firewall problem.
var jdbcUrl = 'jdbc:postgresql://${postgresFqdn}:5432/${postgresDatabase}?sslmode=require'

resource flywayJob 'Microsoft.App/jobs@2024-03-01' = {
  name: 'caj-flyway-${environment}'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      // One identity, unlike caj-db-migration-{env}'s three. This job assumes no other
      // role's rights - it pulls an image and reads one secret.
      '${migrationIdentityId}': {}
    }
  }
  properties: {
    environmentId: environmentId
    configuration: {
      // Manual, never scheduled. The `migrate` stage of deploy.yml starts it and gates on
      // the result: a failure stops the run before any revision is created and before any
      // traffic moves (W-54 section 3, and section 4 break 4).
      triggerType: 'Manual'
      // 1800 seconds. Longer than caj-db-migration-{env}'s 900 because this job's slow
      // case is a large table rewrite, not a bootstrap - and a migration killed halfway by
      // a timeout leaves flyway_schema_history holding a failed row that the next run must
      // be repaired past by hand.
      replicaTimeout: 1800
      // No retry, and this one is not a preference. Flyway takes a lock and records each
      // script; re-running after a partial failure can apply a script twice or collide
      // with the first replica's lock. A failed migration is a finding, and it must stop
      // the deploy rather than be retried into a green run.
      replicaRetryLimit: 0
      manualTriggerConfig: {
        parallelism: 1
        replicaCompletionCount: 1
      }
      // Reader for a secret W-56 already defines. No value here, only the name and the
      // identity allowed to fetch it over the vault private endpoint.
      secrets: [
        {
          name: migrationPasswordSecretName
          // az.environment(), not environment(): the parameter above shadows the function
          // name (BCP265).
          keyVaultUrl: 'https://${keyVaultName}.${az.environment().suffixes.keyvaultDns}/secrets/${migrationPasswordSecretName}'
          identity: migrationIdentityId
        }
      ]
      registries: [
        {
          server: acrLoginServer
          // AcrPull is held by id-migration-{env} (W-51 section 3f, last row). No username,
          // no password, no admin user on the registry.
          identity: migrationIdentityId
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'flyway'
          image: '${acrLoginServer}/${backendImageRepository}:${backendImageTag}'
          resources: {
            // 0.5 / 1.0Gi. The entrypoint sizes the heap from the cgroup limit
            // (-XX:MaxRAMPercentage=75, backend-entrypoint.sh:52-56), so a JVM at 0.5Gi
            // gets ~384MB and can OOM while Flyway parses a large script - a failure that
            // reads like a corrupt migration.
            cpu: json('0.5')
            memory: '1.0Gi'
          }
          env: [
            // The role selector. backend-entrypoint.sh:22-27 maps this to
            // /app/migration.jar; anything else here runs the API or the worker instead
            // and applies nothing - which is precisely the #138 failure.
            { name: 'INFINEVO_ROLE', value: 'migration' }
            // These three names are read verbatim by
            // code/backend/migration/src/main/resources/application.yml:7-9. They are NOT
            // DB_USERNAME/DB_PASSWORD - the migration module uses its own pair so a
            // misconfigured app credential can never be used to change the schema.
            { name: 'DB_URL', value: jdbcUrl }
            { name: 'DB_MIGRATION_USERNAME', value: migrationUsername }
            { name: 'DB_MIGRATION_PASSWORD', secretRef: migrationPasswordSecretName }
            // Not read by the jar. Present so a run can be traced to an identity in the
            // Key Vault audit log without opening the deployment.
            { name: 'ENVIRONMENT', value: environment }
            { name: 'AZURE_CLIENT_ID', value: migrationIdentityClientId }
          ]
        }
      ]
    }
  }
}

output jobId string = flywayJob.id
output jobName string = flywayJob.name
output imageReference string = '${acrLoginServer}/${backendImageRepository}:${backendImageTag}'
