targetScope = 'subscription'

@description('Target deployment environment: dev, uat, prod')
@allowed([
  'dev'
  'uat'
  'prod'
])
param environment string

@description('Primary Azure region for all resources (D-18: Central India)')
param location string = 'centralindia'

@description('PostgreSQL server administrator username')
param postgresAdminUsername string = 'infinevo_admin'

@description('PostgreSQL server administrator password')
@secure()
param postgresAdminPassword string

// SKU & Capacity overrides
@description('PostgreSQL compute SKU')
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

@description('ACR SKU tier')
param acrSku string = 'Basic'

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
    sku: acrSku
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

// ── 3. Environment Group Resources ───────────────────────────────────────────
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
    logAnalyticsSharedKey: logAnalytics.outputs.primarySharedKey
    tags: defaultTags
  }
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

module serviceBus 'modules/servicebus.bicep' = {
  name: 'deploy-servicebus-${environment}'
  scope: envRg
  params: {
    namespaceName: 'sb-infinevo-${environment}'
    location: location
    skuName: 'Standard'
    tags: defaultTags
  }
}

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
    tags: defaultTags
  }
  dependsOn: [
    acrRoleAssignment
  ]
}

// ── Outputs ──────────────────────────────────────────────────────────────────
output sharedResourceGroup string = sharedRgName
output environmentResourceGroup string = envRgName
output registryLoginServer string = registry.outputs.loginServer
output keyVaultUri string = keyVault.outputs.keyVaultUri
output postgresFqdn string = postgres.outputs.fullyQualifiedDomainName
output redisHostName string = redis.outputs.hostName
output serviceBusEndpoint string = serviceBus.outputs.endpoint
output storageBlobEndpoint string = storage.outputs.primaryBlobEndpoint
output webAppFqdn string = containerApps.outputs.webFqdn
output apiAppFqdn string = containerApps.outputs.appFqdn
output keycloakFqdn string = containerApps.outputs.keycloakFqdn
