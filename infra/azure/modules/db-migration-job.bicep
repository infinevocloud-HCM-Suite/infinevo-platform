// ─────────────────────────────────────────────────────────────────────────────
// db-migration-job.bicep — caj-db-migration-{env} (W-51 section 3e).
//
// A manual-trigger Container Apps job that runs infra/postgres/provision.sh against the
// private PostgreSQL endpoint and then the six private-path probes of W-51 section 5
// step 6. It exists because provision.sh has to reach a server with
// publicNetworkAccess: 'Disabled', which nothing outside the VNet can do - it is the
// replacement for the transient public firewall rule that post-deploy-db.sh used to open.
//
// PLACEMENT ON snet-cae IS INHERITED, NOT SET HERE. A Container Apps job has no subnet
// property of its own; it runs in the infrastructure subnet of its managed environment,
// which containerapp-env.bicep pins to snet-cae. If cae-infinevo-{env} is ever deployed
// without vnetConfiguration, this job silently loses its private route and every probe
// below fails on the 10.x assertion rather than passing over a public endpoint.
//
// NO SECRET VALUE IS PASSED IN. Every credential the job needs - the PostgreSQL
// administrator password and the four role passwords - is read from Key Vault at run time
// by the entrypoint, using id-migration-{env}'s Key Vault Secrets User role over the vault
// private endpoint. The env block below carries names and endpoints only.
// ─────────────────────────────────────────────────────────────────────────────

@description('Target environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Resource id of the VNet-injected Container Apps environment (cae-infinevo-{env})')
param environmentId string

@description('ACR login server the runner image is pulled from (e.g. crinfinevo.azurecr.io)')
param acrLoginServer string = 'crinfinevo.azurecr.io'

@description('Repository and tag of the migration runner image built from infra/docker/migration-runner.Dockerfile')
param runnerImage string = 'migration-runner:latest'

@description('Resource id of id-migration-{env} - pulls the image and reads Key Vault secrets')
param migrationIdentityId string

@description('Client id of id-migration-{env}, used by the entrypoint for `az login --identity`')
param migrationIdentityClientId string

@description('Resource id of id-app-{env}. Attached so probes 6c, 6d and the send half of 6e run AS THE APP IDENTITY - an identity can only be assumed by compute it is assigned to, so exercising its roles from this job requires it here.')
param appIdentityId string

@description('Client id of id-app-{env}')
param appIdentityClientId string

@description('Resource id of id-worker-{env}. Attached for the receive half of probe 6e, which must run as the identity holding Storage Queue Data Message Processor.')
param workerIdentityId string

@description('Client id of id-worker-{env}')
param workerIdentityClientId string

@description('Name of the shared Key Vault the job reads secrets from')
param keyVaultName string = 'kv-infinevo-shared'

@description('Name of the environment storage account carrying the blob containers and queues')
param storageAccountName string

@description('Blob service endpoint of the storage account, e.g. https://stinfinevodev.blob.core.windows.net/')
param blobEndpoint string

@description('Queue service endpoint of the storage account, e.g. https://stinfinevodev.queue.core.windows.net/')
param queueEndpoint string

@description('Redis host name, e.g. redis-infinevo-dev.redis.cache.windows.net')
param redisHostName string

@description('Fully qualified domain name of the PostgreSQL flexible server. Resolves to a 10.x address inside the VNet via privatelink.postgres.database.azure.com.')
param postgresFqdn string

@description('PostgreSQL administrator username used by provision.sh')
param postgresAdminUsername string = 'infinevo_admin'

@description('PostgreSQL database provisioned by provision.sh')
param postgresDatabase string = 'infinevo'

@description('Tags for the resource')
param tags object = {}

resource migrationJob 'Microsoft.App/jobs@2024-03-01' = {
  name: 'caj-db-migration-${environment}'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${migrationIdentityId}': {}
      '${appIdentityId}': {}
      '${workerIdentityId}': {}
    }
  }
  properties: {
    environmentId: environmentId
    configuration: {
      // Manual, never scheduled. deploy.sh starts it and gates on the result (W-51
      // section 3e, "Gate"); W-51 section 5 step 6 starts it again on every verification
      // pass, which is safe because provision.sh is idempotent (section 3e,
      // "Idempotency").
      triggerType: 'Manual'
      // 900 seconds, matching the poll deadline in W-51 section 5 step 6. A first
      // bootstrap on a Burstable B1ms server is the slow case.
      replicaTimeout: 900
      // No retry. A failed provision.sh or a failed probe is a real finding and must
      // surface as Failed; retrying would turn an intermittent private-path fault into a
      // green run, which is exactly what deliberate break 6 tests for.
      replicaRetryLimit: 0
      manualTriggerConfig: {
        parallelism: 1
        replicaCompletionCount: 1
      }
      registries: [
        {
          server: acrLoginServer
          // AcrPull is held by id-migration-{env} (W-51 section 3f, last row). No
          // username, no password, no admin user on the registry.
          identity: migrationIdentityId
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'migration-runner'
          image: '${acrLoginServer}/${runnerImage}'
          resources: {
            // 0.5 / 1.0Gi is the smallest Consumption combination that leaves headroom for
            // the Azure CLI, which is a Python process and will OOM at 0.5Gi partway
            // through a probe - a failure mode that looks like a network fault.
            cpu: json('0.5')
            memory: '1.0Gi'
          }
          env: [
            { name: 'ENVIRONMENT', value: environment }
            { name: 'AZURE_SUBSCRIPTION_ID', value: subscription().subscriptionId }
            { name: 'KEY_VAULT_NAME', value: keyVaultName }
            { name: 'STORAGE_ACCOUNT_NAME', value: storageAccountName }
            { name: 'BLOB_ENDPOINT', value: blobEndpoint }
            { name: 'QUEUE_ENDPOINT', value: queueEndpoint }
            { name: 'REDIS_HOST', value: redisHostName }
            // provision.sh reads PGHOST/PGPORT/PGUSER/PGDATABASE from the environment
            // (infra/postgres/provision.sh:9-21) and is run unchanged - W-51 section 3c.
            { name: 'PGHOST', value: postgresFqdn }
            { name: 'PGPORT', value: '5432' }
            { name: 'PGUSER', value: postgresAdminUsername }
            { name: 'PGDATABASE', value: postgresDatabase }
            { name: 'MIGRATION_CLIENT_ID', value: migrationIdentityClientId }
            { name: 'APP_CLIENT_ID', value: appIdentityClientId }
            { name: 'WORKER_CLIENT_ID', value: workerIdentityClientId }
          ]
        }
      ]
    }
  }
}

output jobId string = migrationJob.id
output jobName string = migrationJob.name
