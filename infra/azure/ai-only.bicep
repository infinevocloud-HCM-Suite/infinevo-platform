// ai-only.bicep — W-114.2: create ONLY the AI helper's Azure resources in an environment that
// already exists, without a full main.bicep run.
//
// Why this file exists (founder decision 2026-11-23): a full deploy.sh run would undo settings
// made by hand on the live environment that main.bicep does not declare — the custom domain on
// the Front Door routes and the WAF association, and the storage account's network lockdown.
// Until those are in the templates, the AI resources go in through this narrow deployment,
// which touches nothing that exists today.
//
// It creates, in the environment resource group, exactly what main.bicep creates when
// deployAi = true, with the same names and tags, so a later full run sees them as its own:
//   - the Azure OpenAI account, its model deployment, the app identity's role, the budget
//     (modules/ai-openai.bicep)
//   - the private DNS zone privatelink.openai.azure.com and its link to the VNet
//   - the private endpoint into snet-pe (modules/private-endpoint.bicep)
// It does NOT change any container app: the two AI settings reach the app through deploy.yml
// once W-114.4 reads them.
//
// Preview:  az deployment group what-if -g rg-infinevo-dev -f infra/azure/ai-only.bicep -p environment=dev
// Deploy:   az deployment group create  -g rg-infinevo-dev -f infra/azure/ai-only.bicep -p environment=dev
targetScope = 'resourceGroup'

@description('Environment short name; the resource group must be rg-infinevo-<environment>')
@allowed(['dev', 'uat', 'prod'])
param environment string

@description('Region of the VNet, the private endpoint and the zone link — the environment region')
@allowed(['centralindia'])
param location string = 'centralindia'

// The AI parameters below carry the same defaults as main.bicep (W-114.2).
@allowed(['southindia', 'centralindia', 'swedencentral', 'eastus2'])
param aiLocation string = 'southindia'
param aiModelName string = 'gpt-4.1-mini'
param aiModelVersion string = '2025-04-14'
@allowed(['Standard', 'GlobalStandard', 'DataZoneStandard'])
param aiDeploymentSku string = 'Standard'
param aiCapacity int = 100
param aiMonthlyBudget int = 5000
param aiBudgetStartDate string = '2026-12-01'
param alertEmail string = 'kmohapatra@infinevocloud.com'

var tags = {
  Project: 'Infinevo'
  ManagedBy: 'Bicep'
  Environment: environment
}

resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' existing = {
  name: 'vnet-infinevo-${environment}'
}

resource peSubnet 'Microsoft.Network/virtualNetworks/subnets@2023-11-01' existing = {
  parent: vnet
  name: 'snet-pe'
}

resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = {
  name: 'id-app-${environment}'
}

module aiOpenAi 'modules/ai-openai.bicep' = {
  name: 'deploy-ai-openai-${environment}'
  params: {
    environment: environment
    aiLocation: aiLocation
    aiModelName: aiModelName
    aiModelVersion: aiModelVersion
    aiDeploymentSku: aiDeploymentSku
    aiCapacity: aiCapacity
    // The app only. The worker never calls the model.
    appPrincipalId: appIdentity.properties.principalId
    alertEmail: alertEmail
    aiMonthlyBudget: aiMonthlyBudget
    aiBudgetStartDate: aiBudgetStartDate
    tags: tags
  }
}

// Same zone and link as modules/private-dns.bicep declares with deployAi = true.
resource openAiZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.openai.azure.com'
  location: 'global'
  tags: tags
}

resource openAiZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: openAiZone
  name: 'link-${vnet.name}'
  location: 'global'
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnet.id
    }
  }
}

module aiPrivateEndpoint 'modules/private-endpoint.bicep' = {
  name: 'deploy-pe-ai-openai-${environment}'
  params: {
    privateEndpointName: 'pe-oai-infinevo-${environment}'
    location: location
    subnetId: peSubnet.id
    targetResourceId: aiOpenAi.outputs.accountId
    groupId: 'account'
    privateDnsZoneId: openAiZone.id
    tags: tags
  }
}

output aiEndpoint string = aiOpenAi.outputs.endpoint
output aiDeploymentName string = aiOpenAi.outputs.deploymentName
