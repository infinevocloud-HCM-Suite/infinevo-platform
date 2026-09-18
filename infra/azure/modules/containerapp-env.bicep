@description('Name of the Container Apps Environment')
param environmentName string

@description('Azure region for the environment')
param location string = resourceGroup().location

@description('Log Analytics customer ID')
param logAnalyticsCustomerId string

@description('Name of the Log Analytics workspace supplying the shared key')
param logAnalyticsWorkspaceName string

@description('Resource group holding the shared Log Analytics workspace')
param sharedResourceGroupName string

@description('Tags for the resource')
param tags object = {}

// Resolved here rather than passed in, so the key is never a module output (review F-3).
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' existing = {
  name: logAnalyticsWorkspaceName
  scope: resourceGroup(sharedResourceGroupName)
}

resource containerAppEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: environmentName
  location: location
  tags: tags
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsCustomerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    zoneRedundant: false
  }
}

output environmentId string = containerAppEnv.id
output environmentName string = containerAppEnv.name
output defaultDomain string = containerAppEnv.properties.defaultDomain
output staticIp string = containerAppEnv.properties.staticIp
