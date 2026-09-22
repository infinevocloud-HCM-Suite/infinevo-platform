@description('Resource id of the virtual network each zone is linked to')
param vnetId string

@description('Name of the virtual network, used to name the zone links')
param vnetName string

@description('Tags for the resources')
param tags object = {}

// Private DNS zones are global resources: `location` is always 'global', never the
// deployment region. Section 5 step 7 of W-51 excludes them from the centralindia
// residency assertion for that reason - the zone holds no data, only A records.
var zoneLocation = 'global'

// Registration is disabled on every link: these zones carry private endpoint A records
// written by the DNS zone groups in private-endpoint.bicep, not VM auto-registration.
resource postgresZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.postgres.database.azure.com'
  location: zoneLocation
  tags: tags
}

resource postgresZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: postgresZone
  name: 'link-${vnetName}'
  location: zoneLocation
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnetId
    }
  }
}

resource redisZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.redisenterprise.cache.azure.net'
  location: zoneLocation
  tags: tags
}

resource redisZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: redisZone
  name: 'link-${vnetName}'
  location: zoneLocation
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnetId
    }
  }
}

// No privatelink.servicebus.windows.net zone: there is no Service Bus namespace. Founder
// decision 2026-09-19 replaced it with Azure Storage Queues on stinfinevo{env}, because a
// private endpoint on Service Bus needs the Premium tier (~10x) while Storage Queue gets
// one on Standard. The queue zone below is what carries its A record instead.
resource blobZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.blob.${environment().suffixes.storage}'
  location: zoneLocation
  tags: tags
}

resource blobZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: blobZone
  name: 'link-${vnetName}'
  location: zoneLocation
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnetId
    }
  }
}

// Queue is a distinct private link sub-resource from blob and resolves under its own
// zone, so the blob zone above cannot serve it - a queue endpoint with only the blob zone
// linked leaves every client resolving the public name (W-51 section 5 step 6).
resource queueZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.queue.${environment().suffixes.storage}'
  location: zoneLocation
  tags: tags
}

resource queueZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: queueZone
  name: 'link-${vnetName}'
  location: zoneLocation
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnetId
    }
  }
}

resource vaultZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.vaultcore.azure.net'
  location: zoneLocation
  tags: tags
}

resource vaultZoneLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: vaultZone
  name: 'link-${vnetName}'
  location: zoneLocation
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnetId
    }
  }
}

output postgresZoneId string = postgresZone.id
output redisZoneId string = redisZone.id
output blobZoneId string = blobZone.id
output queueZoneId string = queueZone.id
output vaultZoneId string = vaultZone.id
