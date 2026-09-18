@description('Name of the Azure Cache for Redis instance')
param redisName string

@description('Azure region')
param location string = resourceGroup().location

@description('Redis SKU family: C (Basic/Standard), P (Premium)')
@allowed([
  'C'
  'P'
])
param skuFamily string = 'C'

@description('Redis SKU name: Basic, Standard, Premium')
@allowed([
  'Basic'
  'Standard'
  'Premium'
])
param skuName string = 'Basic'

@description('Redis SKU capacity: 0 (250MB for Basic/Standard), 1 (1GB for Basic/Standard), etc.')
param skuCapacity int = 0

@description('Tags for the resource')
param tags object = {}

resource redisCache 'Microsoft.Cache/redis@2023-08-01' = {
  name: redisName
  location: location
  tags: tags
  properties: {
    sku: {
      name: skuName
      family: skuFamily
      capacity: skuCapacity
    }
    enableNonSslPort: false
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
  }
}

output redisId string = redisCache.id
output redisName string = redisCache.name
output hostName string = redisCache.properties.hostName
output sslPort int = redisCache.properties.sslPort
