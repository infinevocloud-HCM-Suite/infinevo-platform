@description('Name of the Log Analytics workspace')
param workspaceName string = 'law-infinevo-shared'

@description('Azure region for the workspace')
param location string = resourceGroup().location

@description('Log retention in days')
param retentionInDays int = 30

@description('Tags for the resource')
param tags object = {}

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: workspaceName
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
  }
}

output workspaceId string = logAnalytics.id
output workspaceName string = logAnalytics.name
output customerId string = logAnalytics.properties.customerId

// The primary shared key is deliberately NOT an output (review F-3). Module outputs are
// persisted in the resource group's ARM deployment history and are readable by anyone
// holding Microsoft.Resources/deployments/read. containerapp-env.bicep resolves the key
// itself from an `existing` reference instead, so it never crosses a module boundary.
