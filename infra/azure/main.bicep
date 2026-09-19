targetScope = 'subscription'

@description('Target deployment environment: dev, uat, prod')
@allowed([
  'dev'
  'uat'
  'prod'
])
param environment string

@description('Primary Azure region for all resources (D-18: Central India)')
// Single-valued on purpose. D-18 puts Indian payroll data under the DPDP Act, so a
// deployment to any other region is a compliance breach, not a configuration choice.
// W-51 deliberate break 7 is `--location eastus` being rejected here, before anything
// is created.
@allowed([
  'centralindia'
])
param location string = 'centralindia'

@description('Object id of the principal running the deployment, granted Key Vault Secrets Officer (review F-5). deploy.sh supplies it from `az ad signed-in-user show`.')
param deployerObjectId string = ''

@description('PostgreSQL server administrator username')
param postgresAdminUsername string = 'infinevo_admin'

@description('PostgreSQL server administrator password')
@secure()
param postgresAdminPassword string = ''

// SKU & Capacity overrides
@description('PostgreSQL compute SKU')
@allowed([
  'Standard_B1ms'
  'Standard_B2s'
  'Standard_D2ds_v5'
  'Standard_D4ds_v5'
])
param postgresSkuName string = 'Standard_B1ms'

@description('PostgreSQL compute tier')
param postgresSkuTier string = 'Burstable'

@description('PostgreSQL storage in GB')
param postgresStorageSizeGB int = 32

@description('PostgreSQL high availability mode')
param postgresHighAvailability string = 'Disabled'

@description('Redis SKU family')
param redisSkuFamily string = 'C'

@description('Redis SKU name')
param redisSkuName string = 'Basic'

@description('Redis SKU capacity')
param redisSkuCapacity int = 0

@description('Storage Account SKU')
param storageSkuName string = 'Standard_LRS'

@description('Container App CPU allocation')
param containerAppCpu string = '0.25'

@description('Container App Memory allocation')
param containerAppMemory string = '0.5Gi'

@description('Container App minimum replicas')
param containerAppMinReplicas int = 0

@description('Container App maximum replicas')
param containerAppMaxReplicas int = 3

@description('Key Vault purge protection enabled')
param enablePurgeProtection bool = false

// ── Networking (W-51) ────────────────────────────────────────────────────────
@description('VNet address space, 10.{octet}.0.0/16 - dev 10.10, uat 10.20, prod 10.30')
param vnetAddressPrefix string

@description('Address prefix for snet-cae, the Container Apps environment subnet (a /23)')
param caeSubnetPrefix string

@description('Address prefix for snet-pe, the private endpoint subnet (a /24)')
param peSubnetPrefix string

@description('Transient Key Vault network ACL ip rules, each an object with a value property holding a CIDR. Empty at rest - deploy.sh adds and revokes its own egress address within one run (W-51 section 2.3)')
param keyVaultAllowedIpRules array = []

@description('AzureFrontDoor.Backend IP prefixes (CIDR strings) allowed to reach Container Apps ingress. Empty at rest - deploy.sh resolves the service tag at deploy time, and an empty list means ingress denies everything (W-51 section 2.5)')
param frontDoorBackendPrefixes array = []

var sharedRgName = 'rg-infinevo-shared'
var envRgName = 'rg-infinevo-${environment}'

var defaultTags = {
  Project: 'Infinevo'
  ManagedBy: 'Bicep'
  Environment: environment
}

// ── 1. Resource Groups ────────────────────────────────────────────────────────
resource sharedRg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: sharedRgName
  location: location
  tags: defaultTags
}

resource envRg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: envRgName
  location: location
  tags: defaultTags
}

// ── 2. Shared Group Resources ─────────────────────────────────────────────────
module registry 'modules/registry.bicep' = {
  name: 'deploy-registry'
  scope: sharedRg
  params: {
    registryName: 'crinfinevo'
    location: location
    // Fixed, NOT acrSku from the environment parameter file: this registry is shared by
    // dev, uat and prod, so a dev deployment must not be able to re-submit it at a lower
    // tier and downgrade production (review F-6).
    sku: 'Standard'
    tags: defaultTags
  }
}

module keyVault 'modules/keyvault.bicep' = {
  name: 'deploy-keyvault'
  scope: sharedRg
  params: {
    keyVaultName: 'kv-infinevo-shared'
    location: location
    enablePurgeProtection: enablePurgeProtection
    deployerObjectId: deployerObjectId
    allowedIpRules: keyVaultAllowedIpRules
    tags: defaultTags
  }
}

module logAnalytics 'modules/loganalytics.bicep' = {
  name: 'deploy-loganalytics'
  scope: sharedRg
  params: {
    workspaceName: 'law-infinevo-shared'
    location: location
    retentionInDays: 30
    tags: defaultTags
  }
}

// ── 3. Environment Network ───────────────────────────────────────────────────
// Deployed before the Container Apps environment and before every private endpoint:
// snet-cae has to exist before cae-infinevo-{env} can be injected into it (T2), and a
// private endpoint cannot be created without its subnet or resolve without its zone.
module vnet 'modules/vnet.bicep' = {
  name: 'deploy-vnet-${environment}'
  scope: envRg
  params: {
    vnetName: 'vnet-infinevo-${environment}'
    location: location
    vnetAddressPrefix: vnetAddressPrefix
    caeSubnetPrefix: caeSubnetPrefix
    peSubnetPrefix: peSubnetPrefix
    tags: defaultTags
  }
}

module privateDns 'modules/private-dns.bicep' = {
  name: 'deploy-private-dns-${environment}'
  scope: envRg
  params: {
    vnetId: vnet.outputs.vnetId
    vnetName: vnet.outputs.vnetName
    tags: defaultTags
  }
}

// ── 4. Environment Group Resources ───────────────────────────────────────────
module managedIdentities 'modules/managed-identities.bicep' = {
  name: 'deploy-identities-${environment}'
  scope: envRg
  params: {
    environment: environment
    location: location
    tags: defaultTags
  }
}

// AcrPull role assignment for managed identities on shared registry
module acrRoleAssignment 'modules/acr-role-assignment.bicep' = {
  name: 'assign-acrpull-${environment}'
  scope: sharedRg
  params: {
    registryName: registry.outputs.registryName
    principalIds: [
      managedIdentities.outputs.appIdentityPrincipalId
      managedIdentities.outputs.workerIdentityPrincipalId
      managedIdentities.outputs.webIdentityPrincipalId
      managedIdentities.outputs.keycloakIdentityPrincipalId
      // W-51 section 3f, last row: all five identities hold AcrPull. id-migration needs it
      // to pull migration-runner:latest - without it caj-db-migration-{env} cannot start
      // and the failure surfaces as an image-pull error, not as a missing role.
      managedIdentities.outputs.migrationIdentityPrincipalId
    ]
  }
}

module containerAppEnv 'modules/containerapp-env.bicep' = {
  name: 'deploy-cae-${environment}'
  scope: envRg
  params: {
    environmentName: 'cae-infinevo-${environment}'
    location: location
    logAnalyticsCustomerId: logAnalytics.outputs.customerId
    logAnalyticsWorkspaceName: logAnalytics.outputs.workspaceName
    sharedResourceGroupName: sharedRg.name
    infrastructureSubnetId: vnet.outputs.caeSubnetId
    tags: defaultTags
  }
  // No explicit dependsOn: consuming vnet.outputs.caeSubnetId above already forces the
  // ordering section 8a requires - network first, environment second - and the linter
  // rejects the redundant entry (no-unnecessary-dependson). The subnet id is immutable
  // once the environment exists, so this ordering is not merely a convenience.
}

module postgres 'modules/postgres.bicep' = {
  name: 'deploy-postgres-${environment}'
  scope: envRg
  params: {
    serverName: 'psql-infinevo-${environment}'
    location: location
    postgresAdminUsername: postgresAdminUsername
    postgresAdminPassword: postgresAdminPassword
    skuName: postgresSkuName
    skuTier: postgresSkuTier
    storageSizeGB: postgresStorageSizeGB
    highAvailabilityMode: postgresHighAvailability
    tags: defaultTags
  }
}

module redis 'modules/redis.bicep' = {
  name: 'deploy-redis-${environment}'
  scope: envRg
  params: {
    redisName: 'redis-infinevo-${environment}'
    location: location
    skuFamily: redisSkuFamily
    skuName: redisSkuName
    skuCapacity: redisSkuCapacity
    tags: defaultTags
  }
}

// No Service Bus namespace. Founder decision 2026-09-19: queueing moves to Azure Storage
// Queues on stinfinevo{env} below, because a private endpoint on Service Bus requires the
// Premium tier (~10x Standard) and D-19 scale does not justify it. Storage Queue takes a
// private endpoint on the Standard account that already exists, so the perimeter rule at
// docs/target-state/05-azure-architecture.md:75 holds at near-zero cost.
module storage 'modules/storage.bicep' = {
  name: 'deploy-storage-${environment}'
  scope: envRg
  params: {
    storageAccountName: 'stinfinevo${environment}'
    location: location
    skuName: storageSkuName
    tags: defaultTags
  }
}

var identityMap = {
  app: {
    id: managedIdentities.outputs.appIdentityId
    clientId: managedIdentities.outputs.appIdentityClientId
  }
  worker: {
    id: managedIdentities.outputs.workerIdentityId
    clientId: managedIdentities.outputs.workerIdentityClientId
  }
  web: {
    id: managedIdentities.outputs.webIdentityId
    clientId: managedIdentities.outputs.webIdentityClientId
  }
  keycloak: {
    id: managedIdentities.outputs.keycloakIdentityId
    clientId: managedIdentities.outputs.keycloakIdentityClientId
  }
}

module containerApps 'modules/containerapps.bicep' = {
  name: 'deploy-containerapps-${environment}'
  scope: envRg
  params: {
    environment: environment
    location: location
    environmentId: containerAppEnv.outputs.environmentId
    acrLoginServer: registry.outputs.loginServer
    cpu: containerAppCpu
    memory: containerAppMemory
    minReplicas: containerAppMinReplicas
    maxReplicas: containerAppMaxReplicas
    identities: identityMap
    frontDoorBackendPrefixes: frontDoorBackendPrefixes
    tags: defaultTags
  }
  dependsOn: [
    acrRoleAssignment
  ]
}

// ── 5. Private Endpoints ─────────────────────────────────────────────────────
// All five land in snet-pe - postgres, redis, storage blob, storage queue and the shared
// vault. Each pairs with the zone group inside private-endpoint.bicep,
// which is what writes the A record that makes the public FQDN resolve to a 10.x address.
module postgresPrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-postgres-${environment}'
  scope: envRg
  params: {
    privateEndpointName: 'pe-psql-infinevo-${environment}'
    location: location
    subnetId: vnet.outputs.peSubnetId
    targetResourceId: postgres.outputs.serverId
    groupId: 'postgresqlServer'
    privateDnsZoneId: privateDns.outputs.postgresZoneId
    tags: defaultTags
  }
}

module redisPrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-redis-${environment}'
  scope: envRg
  params: {
    privateEndpointName: 'pe-redis-infinevo-${environment}'
    location: location
    subnetId: vnet.outputs.peSubnetId
    targetResourceId: redis.outputs.redisId
    groupId: 'redisCache'
    privateDnsZoneId: privateDns.outputs.redisZoneId
    tags: defaultTags
  }
}

module storageBlobPrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-storage-blob-${environment}'
  scope: envRg
  params: {
    privateEndpointName: 'pe-st-blob-infinevo-${environment}'
    location: location
    subnetId: vnet.outputs.peSubnetId
    targetResourceId: storage.outputs.storageAccountId
    groupId: 'blob'
    privateDnsZoneId: privateDns.outputs.blobZoneId
    tags: defaultTags
  }
}

// Second endpoint on the same storage account. Blob and queue are distinct private link
// sub-resources with distinct DNS zones, so the blob endpoint above does not reach the
// queues - without this one, publicNetworkAccess: 'Disabled' would leave payrun, import
// and report unreachable from anywhere.
module storageQueuePrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-storage-queue-${environment}'
  scope: envRg
  params: {
    privateEndpointName: 'pe-st-queue-infinevo-${environment}'
    location: location
    subnetId: vnet.outputs.peSubnetId
    targetResourceId: storage.outputs.storageAccountId
    groupId: 'queue'
    privateDnsZoneId: privateDns.outputs.queueZoneId
    tags: defaultTags
  }
}

// The vault itself is shared and lives in rg-infinevo-shared, but snet-pe is per
// environment, so the endpoint is an environment resource pointing across groups. Each
// environment gets its own route to the same vault.
module keyVaultPrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-keyvault-${environment}'
  scope: envRg
  params: {
    privateEndpointName: 'pe-kv-infinevo-shared-${environment}'
    location: location
    subnetId: vnet.outputs.peSubnetId
    targetResourceId: keyVault.outputs.keyVaultId
    groupId: 'vault'
    privateDnsZoneId: privateDns.outputs.vaultZoneId
    tags: defaultTags
  }
}

// ── 6. Front Door ────────────────────────────────────────────────────────────
// Deployed at sharedRg scope, not envRg: the profile is shared by dev, uat and prod and
// rg-infinevo-shared is the permanent group (05-azure-architecture.md:28). Only the
// endpoint, origin groups, routes and security policy inside it are per environment.
//
// Consuming the three ingress FQDNs from containerApps above is what orders this after the
// apps exist - an origin cannot be created against a host name that has not been assigned
// yet. The worker has no ingress and therefore no origin (W-51 section 3a).
module frontDoor 'modules/frontdoor.bicep' = {
  name: 'deploy-frontdoor-${environment}'
  scope: sharedRg
  params: {
    profileName: 'afd-infinevo-shared'
    environment: environment
    webOriginHostName: containerApps.outputs.webFqdn
    appOriginHostName: containerApps.outputs.appFqdn
    keycloakOriginHostName: containerApps.outputs.keycloakFqdn
    tags: defaultTags
  }
}

// ── 7. Data-plane RBAC (W-51 section 3f) ─────────────────────────────────────
// The matrix spans both resource groups: the vault is shared and the storage account is
// per environment.
// Two instantiations of one file, because a role assignment only compiles at the scope of
// the resource it grants (BCP139) and the vault and the storage account are in different
// resource groups. rbac.bicep guards each block on which target it was given.
module keyVaultRbac 'modules/rbac.bicep' = {
  name: 'assign-rbac-keyvault-${environment}'
  scope: sharedRg
  params: {
    environment: environment
    keyVaultName: keyVault.outputs.keyVaultName
    appPrincipalId: managedIdentities.outputs.appIdentityPrincipalId
    workerPrincipalId: managedIdentities.outputs.workerIdentityPrincipalId
    webPrincipalId: managedIdentities.outputs.webIdentityPrincipalId
    keycloakPrincipalId: managedIdentities.outputs.keycloakIdentityPrincipalId
    migrationPrincipalId: managedIdentities.outputs.migrationIdentityPrincipalId
  }
}

module storageRbac 'modules/rbac.bicep' = {
  name: 'assign-rbac-storage-${environment}'
  scope: envRg
  params: {
    environment: environment
    storageAccountName: storage.outputs.storageAccountName
    appPrincipalId: managedIdentities.outputs.appIdentityPrincipalId
    workerPrincipalId: managedIdentities.outputs.workerIdentityPrincipalId
    webPrincipalId: managedIdentities.outputs.webIdentityPrincipalId
    keycloakPrincipalId: managedIdentities.outputs.keycloakIdentityPrincipalId
    migrationPrincipalId: managedIdentities.outputs.migrationIdentityPrincipalId
  }
}

// ── 8. In-VNet migration runner (W-51 section 3e) ────────────────────────────
// Last, and it has to be. The job cannot exist before the VNet-injected environment
// (section 8a step 4), and starting it before dataPlaneRbac would fail probe 6a - so the
// dependsOn below is on the RBAC module, not merely on the endpoints.
module dbMigrationJob 'modules/db-migration-job.bicep' = {
  name: 'deploy-db-migration-job-${environment}'
  scope: envRg
  params: {
    environment: environment
    location: location
    environmentId: containerAppEnv.outputs.environmentId
    acrLoginServer: registry.outputs.loginServer
    migrationIdentityId: managedIdentities.outputs.migrationIdentityId
    migrationIdentityClientId: managedIdentities.outputs.migrationIdentityClientId
    appIdentityId: managedIdentities.outputs.appIdentityId
    appIdentityClientId: managedIdentities.outputs.appIdentityClientId
    workerIdentityId: managedIdentities.outputs.workerIdentityId
    workerIdentityClientId: managedIdentities.outputs.workerIdentityClientId
    keyVaultName: keyVault.outputs.keyVaultName
    storageAccountName: storage.outputs.storageAccountName
    blobEndpoint: storage.outputs.primaryBlobEndpoint
    queueEndpoint: storage.outputs.primaryQueueEndpoint
    redisHostName: redis.outputs.hostName
    postgresFqdn: postgres.outputs.fullyQualifiedDomainName
    postgresAdminUsername: postgresAdminUsername
    tags: defaultTags
  }
  // The private endpoints carry no output this module consumes, so nothing else would
  // order the job after them - and a job that starts before the Postgres or Key Vault
  // endpoint exists resolves a PUBLIC address and fails the 10.x assertion in every probe.
  dependsOn: [
    keyVaultRbac
    storageRbac
    acrRoleAssignment
    postgresPrivateEndpoint
    keyVaultPrivateEndpoint
    storageBlobPrivateEndpoint
    storageQueuePrivateEndpoint
    redisPrivateEndpoint
  ]
}

// ── Outputs ──────────────────────────────────────────────────────────────────
output sharedResourceGroup string = sharedRgName
output environmentResourceGroup string = envRgName
output registryLoginServer string = registry.outputs.loginServer
output keyVaultUri string = keyVault.outputs.keyVaultUri
output postgresFqdn string = postgres.outputs.fullyQualifiedDomainName
output redisHostName string = redis.outputs.hostName
output storageBlobEndpoint string = storage.outputs.primaryBlobEndpoint
// Replaces the former serviceBusEndpoint output. W-52 consumes this to build the queue
// client; there is no connection string to emit, and none is wanted - access is by
// managed identity (W-51 section 3f).
output storageQueueEndpoint string = storage.outputs.primaryQueueEndpoint
output webAppFqdn string = containerApps.outputs.webFqdn
output apiAppFqdn string = containerApps.outputs.appFqdn
output keycloakFqdn string = containerApps.outputs.keycloakFqdn
// The public entry point. There is no custom domain (W-51 decision 1, founder 2026-09-19),
// so this *.azurefd.net name is the only address the platform answers on, and it is what
// W-51 section 5 step 4 drives every Front Door check through.
output frontDoorEndpointHostName string = frontDoor.outputs.endpointHostName
output frontDoorEndpointName string = frontDoor.outputs.endpointName
output frontDoorProfileName string = frontDoor.outputs.profileName
output frontDoorWafPolicyName string = frontDoor.outputs.wafPolicyName
output vnetId string = vnet.outputs.vnetId
output caeSubnetId string = vnet.outputs.caeSubnetId
output peSubnetId string = vnet.outputs.peSubnetId
output migrationJobName string = dbMigrationJob.outputs.jobName
