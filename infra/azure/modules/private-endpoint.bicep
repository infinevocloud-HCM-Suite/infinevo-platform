@description('Name of the private endpoint, e.g. pe-psql-infinevo-dev')
param privateEndpointName string

@description('Azure region (D-18: the private endpoint NIC is a regional resource)')
param location string = resourceGroup().location

@description('Resource id of snet-pe, the subnet the endpoint NIC is placed in')
param subnetId string

@description('Resource id of the service being reached privately. May sit in another resource group - kv-infinevo-shared does')
param targetResourceId string

@description('Private link sub-resource: postgresqlServer, redisCache, blob, queue or vault. Storage exposes blob and queue as separate sub-resources, so the account carries one endpoint of each')
@allowed([
  'postgresqlServer'
  'redisCache'
  'blob'
  'queue'
  'vault'
])
param groupId string

@description('Resource id of the privatelink DNS zone that receives the A record for this endpoint')
param privateDnsZoneId string

@description('Tags for the resources')
param tags object = {}

resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = {
  name: privateEndpointName
  location: location
  tags: tags
  properties: {
    subnet: {
      id: subnetId
    }
    privateLinkServiceConnections: [
      {
        name: '${privateEndpointName}-connection'
        properties: {
          privateLinkServiceId: targetResourceId
          groupIds: [
            groupId
          ]
        }
      }
    ]
  }
}

// The zone group is what writes the A record. Without it the endpoint exists, the NIC
// holds a 10.x address, and every client still resolves the public name to a public IP -
// which is the failure mode section 5 step 6 of W-51 is written to catch.
resource dnsZoneGroup 'Microsoft.Network/privateEndpoints/privateDnsZoneGroups@2023-11-01' = {
  parent: privateEndpoint
  name: 'default'
  properties: {
    privateDnsZoneConfigs: [
      {
        name: '${groupId}-config'
        properties: {
          privateDnsZoneId: privateDnsZoneId
        }
      }
    ]
  }
}

output privateEndpointId string = privateEndpoint.id
output privateEndpointName string = privateEndpoint.name
