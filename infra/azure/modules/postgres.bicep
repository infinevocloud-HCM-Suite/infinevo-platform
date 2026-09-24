@description('Name of the PostgreSQL Flexible Server')
param serverName string

@description('Azure region')
param location string = resourceGroup().location

@description('PostgreSQL server administrator username')
param postgresAdminUsername string = 'infinevo_admin'

@description('PostgreSQL server administrator password')
@secure()
param postgresAdminPassword string

@description('Compute SKU name')
param skuName string = 'Standard_B1ms'

@description('Compute SKU tier: Burstable, GeneralPurpose, MemoryOptimized')
@allowed([
  'Burstable'
  'GeneralPurpose'
  'MemoryOptimized'
])
param skuTier string = 'Burstable'

@description('Storage size in GB')
param storageSizeGB int = 32

@description('High availability mode')
@allowed([
  'Disabled'
  'ZoneRedundant'
  'SameZone'
])
param highAvailabilityMode string = 'Disabled'

@description('Backup retention days (7-35)')
@minValue(7)
@maxValue(35)
param backupRetentionDays int = 7

@description('Tags for the resource')
param tags object = {}

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  tags: tags
  sku: {
    name: skuName
    tier: skuTier
  }
  properties: {
    version: '16'
    administratorLogin: postgresAdminUsername
    administratorLoginPassword: postgresAdminPassword
    storage: {
      storageSizeGB: storageSizeGB
      autoGrow: 'Enabled'
    }
    highAvailability: {
      mode: highAvailabilityMode
    }
    backup: {
      backupRetentionDays: backupRetentionDays
      // D-18: no geo-replication, so the backup stays in centralindia with the primary.
      geoRedundantBackup: 'Disabled'
    }
    // W-51 section 2.2. No delegatedSubnetResourceId: the server stays in public-access
    // mode - the only mode eligible for a private endpoint - and the endpoint is a
    // separate resource in main.bicep. That keeps this switch reversible (section 8c
    // break-glass) and converts psql-infinevo-{env} in place rather than recreating it.
    network: {
      publicNetworkAccess: 'Disabled'
    }
  }
}

// Database: infinevo
resource infinevoDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgresServer
  name: 'infinevo'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Database: keycloak
resource keycloakDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgresServer
  name: 'keycloak'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// The allow-azure-internal firewall rule is deliberately gone (W-51 section 2.3). With
// publicNetworkAccess Disabled a firewall rule grants nothing, and the rule was the last
// route from outside the VNet - post-deploy-db.sh:65-93 leaned on it and T4 removes that too.

output serverId string = postgresServer.id
output serverName string = postgresServer.name
output fullyQualifiedDomainName string = postgresServer.properties.fullyQualifiedDomainName
output administratorLogin string = postgresAdminUsername
output infinevoDatabaseName string = infinevoDb.name
output keycloakDatabaseName string = keycloakDb.name
