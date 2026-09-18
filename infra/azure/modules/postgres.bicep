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

@description('Whether to allow Azure services and resources access to this server')
param allowAzureIps bool = true

@description('Tags for the resource')
param tags object = {}

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-30' = {
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
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
  }
}

// Database: infinevo
resource infinevoDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-30' = {
  parent: postgresServer
  name: 'infinevo'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Database: keycloak
resource keycloakDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-30' = {
  parent: postgresServer
  name: 'keycloak'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Optional firewall rule to allow Azure-internal traffic for Container Apps and initial provisioning
resource firewallAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-12-30' = if (allowAzureIps) {
  parent: postgresServer
  name: 'allow-azure-internal'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

output serverId string = postgresServer.id
output serverName string = postgresServer.name
output fullyQualifiedDomainName string = postgresServer.properties.fullyQualifiedDomainName
output administratorLogin string = postgresAdminUsername
output infinevoDatabaseName string = infinevoDb.name
output keycloakDatabaseName string = keycloakDb.name
