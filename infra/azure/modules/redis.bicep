@description('Name of the Azure Managed Redis instance')
param redisName string

@description('Azure region')
param location string = resourceGroup().location

@description('Redis Enterprise / Azure Managed Redis SKU name: Balanced_B0, Balanced_B1, etc.')
param skuName string = 'Balanced_B0'

@description('Tags for the resource')
param tags object = {}

resource redisEnterprise 'Microsoft.Cache/redisEnterprise@2024-10-01' = {
  name: redisName
  location: location
  tags: tags
  sku: {
    name: skuName
  }
  properties: {
    minimumTlsVersion: '1.2'
  }
}

resource redisDatabase 'Microsoft.Cache/redisEnterprise/databases@2024-10-01' = {
  parent: redisEnterprise
  name: 'default'
  properties: {
    clusteringPolicy: 'OSSCluster'
    clientProtocol: 'Encrypted'
    port: 10000
    evictionPolicy: 'VolatileLRU'
  }
}

output redisId string = redisEnterprise.id
output redisName string = redisEnterprise.name
output hostName string = redisEnterprise.properties.hostName
output sslPort int = 10000
output databaseId string = redisDatabase.id
