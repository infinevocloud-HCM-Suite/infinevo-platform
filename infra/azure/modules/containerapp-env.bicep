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

@description('Resource id of snet-cae, the subnet the environment is injected into (W-51 section 2.1)')
param infrastructureSubnetId string

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
    // IMMUTABLE. infrastructureSubnetId cannot be added to, removed from or changed on an
    // existing managed environment - the platform rejects the update. Changing it later
    // therefore means DELETING cae-infinevo-{env}, which destroys every Container App and
    // job inside it, and redeploying. W-51 section 8a is the recreation procedure; treat
    // any future change to this value as a destructive operation, not a config tweak.
    vnetConfiguration: {
      infrastructureSubnetId: infrastructureSubnetId
      // Stays external. The apps are reached from Front Door, which is a public service
      // and cannot route to an internal-only environment (W-51 section 2.4). Origin
      // protection is done at the ingress instead, by the ipSecurityRestrictions in
      // containerapps.bicep (W-51 section 2.5) - not by hiding the load balancer.
      internal: false
    }
    zoneRedundant: false
  }
}

output environmentId string = containerAppEnv.id
output environmentName string = containerAppEnv.name
output defaultDomain string = containerAppEnv.properties.defaultDomain
output staticIp string = containerAppEnv.properties.staticIp
