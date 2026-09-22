@description('Name of the virtual network')
param vnetName string

@description('Azure region (D-18: every regional resource is centralindia)')
param location string = resourceGroup().location

@description('VNet address space, 10.{octet}.0.0/16 - dev 10.10, uat 10.20, prod 10.30 (W-51 section 2.1)')
param vnetAddressPrefix string

@description('Address prefix for snet-cae. A /23 is the documented minimum for the Container Apps environment subnet')
param caeSubnetPrefix string

@description('Address prefix for snet-pe, the private endpoint subnet')
param peSubnetPrefix string

@description('Tags for the resources')
param tags object = {}

var caeSubnetName = 'snet-cae'
var peSubnetName = 'snet-pe'

// One NSG per subnet, both with no custom rules. The default rule set already permits
// intra-VNet traffic and the outbound internet access that Container Apps requires to
// pull images and reach its control plane; a hand-written deny set would break the
// environment on creation. The NSGs exist so a later ticket (W-64) can add rules without
// recreating the subnets - a subnet's NSG association is mutable, its address prefix is not.
resource caeNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: 'nsg-${caeSubnetName}-${vnetName}'
  location: location
  tags: tags
  properties: {
    securityRules: []
  }
}

resource peNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: 'nsg-${peSubnetName}-${vnetName}'
  location: location
  tags: tags
  properties: {
    securityRules: []
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: vnetName
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [
        vnetAddressPrefix
      ]
    }
    subnets: [
      {
        // Injected into cae-infinevo-{env} by T2. Deliberately NOT delegated: the
        // environment is Consumption-only (containerapp-env.bicep sets no workloadProfiles),
        // and that form takes an undelegated infrastructure subnet.
        name: caeSubnetName
        properties: {
          addressPrefix: caeSubnetPrefix
          networkSecurityGroup: {
            id: caeNsg.id
          }
        }
      }
      {
        // All five private endpoints, Postgres included. There is no delegated
        // snet-postgres: W-51 section 2.2 chose a private endpoint over VNet integration
        // because the two are mutually exclusive and fixed at server creation, and only
        // the private-endpoint form leaves publicNetworkAccess reversible.
        // 10.{octet}.2.0/24 is left unallocated - it held the delegated subnet in rev 2
        // and is reserved rather than reused.
        name: peSubnetName
        properties: {
          addressPrefix: peSubnetPrefix
          privateEndpointNetworkPolicies: 'Disabled'
          networkSecurityGroup: {
            id: peNsg.id
          }
        }
      }
    ]
  }
}

output vnetId string = vnet.id
output vnetName string = vnet.name
output caeSubnetId string = vnet.properties.subnets[0].id
output peSubnetId string = vnet.properties.subnets[1].id
