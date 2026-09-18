@description('Name of the Container Apps Environment')
param environmentName string

@description('Azure region for the environment')
param location string = resourceGroup().location

@description('Log Analytics customer ID')
param logAnalyticsCustomerId string

@description('Log Analytics primary shared key')
@secure()
param logAnalyticsSharedKey string

@description('Tags for the resource')
param tags object = {}

resource containerAppEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: environmentName
  location: location
  tags: tags
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsCustomerId
        sharedKey: logAnalyticsSharedKey
      }
    }
    zoneRedundant: false
  }
}

output environmentId string = containerAppEnv.id
output environmentName string = containerAppEnv.name
output defaultDomain string = containerAppEnv.properties.defaultDomain
output staticIp string = containerAppEnv.properties.staticIp
