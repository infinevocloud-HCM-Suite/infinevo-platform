// ─────────────────────────────────────────────────────────────────────────────
// data-migration-job.bicep — caj-data-migration-{env} (W-71 / W-68).
//
// A manual-trigger Container Apps job that runs the legacy HRMS and Payroll data
// migration against the private PostgreSQL endpoint, Keycloak, and Blob Storage.
//
// PLACEMENT ON snet-cae IS INHERITED, NOT SET HERE. A Container Apps job has no
// subnet property of its own; it runs in the infrastructure subnet of its managed
// environment, which containerapp-env.bicep pins to snet-cae.
//
// NO SECRET VALUE IS PASSED IN. Every credential the job needs is read from
// Key Vault at run time by the entrypoint, using id-migration-{env}'s
// Key Vault Secrets User role over the vault private endpoint.
// ─────────────────────────────────────────────────────────────────────────────

@description('Target environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Resource id of the VNet-injected Container Apps environment (cae-infinevo-{env})')
param environmentId string

@description('ACR login server the runner image is pulled from (e.g. crinfinevo.azurecr.io)')
param acrLoginServer string = 'crinfinevo.azurecr.io'

@description('Repository of the migration runner image built from infra/docker/data-migration.Dockerfile')
param runnerImageRepository string = 'data-migration'

@description('Tag of the migration runner image. Parameterised matching W-54 finding F-16.')
param runnerImageTag string = 'latest'

@description('Resource id of id-migration-{env} - pulls the image, reads Key Vault secrets, accesses Blob storage')
param migrationIdentityId string

@description('Client id of id-migration-{env}, used by the entrypoint for `az login --identity`')
param migrationIdentityClientId string

@description('Name of the shared Key Vault the job reads secrets from')
param keyVaultName string = 'kv-infinevo-shared'

@description('Name of the environment storage account carrying the blob containers')
param storageAccountName string

@description('Blob service endpoint of the storage account, e.g. https://stinfinevodev.blob.core.windows.net/')
param blobEndpoint string

@description('Fully qualified domain name of the PostgreSQL flexible server')
param postgresFqdn string

@description('PostgreSQL database name')
param postgresDatabase string = 'infinevo'

@description('Fully qualified domain name or URL of Keycloak')
param keycloakUrl string

@description('Optional run identifier for replaying dumps from migration-dumps/{runId}/')
param runId string = ''

@description('Tags for the resource')
param tags object = {}

resource dataMigrationJob 'Microsoft.App/jobs@2024-03-01' = {
  name: 'caj-data-migration-${environment}'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${migrationIdentityId}': {}
    }
  }
  properties: {
    environmentId: environmentId
    configuration: {
      triggerType: 'Manual'
      // 14400 seconds (4 hours) timeout for full dataset migration and verification
      replicaTimeout: 14400
      replicaRetryLimit: 0
      manualTriggerConfig: {
        parallelism: 1
        replicaCompletionCount: 1
      }
      registries: [
        {
          server: acrLoginServer
          identity: migrationIdentityId
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'data-migration'
          image: '${acrLoginServer}/${runnerImageRepository}:${runnerImageTag}'
          resources: {
            cpu: json('2.0')
            memory: '4.0Gi'
          }
          env: [
            { name: 'ENVIRONMENT', value: environment }
            { name: 'RUN_ID', value: runId }
            { name: 'APPSETTING_WEBSITE_SITE_NAME', value: 'azcli-workaround' }
            { name: 'AZURE_SUBSCRIPTION_ID', value: subscription().subscriptionId }
            { name: 'KEY_VAULT_NAME', value: keyVaultName }
            { name: 'STORAGE_ACCOUNT_NAME', value: storageAccountName }
            { name: 'BLOB_ENDPOINT', value: blobEndpoint }
            { name: 'TARGET_DB_HOST', value: postgresFqdn }
            { name: 'TARGET_DB_PORT', value: '5432' }
            { name: 'TARGET_DB_NAME', value: postgresDatabase }
            { name: 'TARGET_DB_USER', value: 'migration_user' }
            { name: 'KEYCLOAK_URL', value: keycloakUrl }
            { name: 'KEYCLOAK_REALM', value: 'infinevo' }
            { name: 'KEYCLOAK_ADMIN_USER', value: 'admin' }
            { name: 'MIGRATION_CLIENT_ID', value: migrationIdentityClientId }
          ]
        }
      ]
    }
  }
}

output jobId string = dataMigrationJob.id
output jobName string = dataMigrationJob.name
