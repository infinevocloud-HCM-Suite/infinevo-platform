@description('Name of the Service Bus namespace')
param namespaceName string

@description('Azure region')
param location string = resourceGroup().location

@description('Service Bus SKU')
@allowed([
  'Basic'
  'Standard'
  'Premium'
])
param skuName string = 'Standard'

@description('Tags for the resource')
param tags object = {}

resource serviceBusNamespace 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: namespaceName
  location: location
  tags: tags
  sku: {
    name: skuName
    tier: skuName
  }
  properties: {
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
  }
}

var queueNames = [
  'payrun'
  'import'
  'report'
]

resource queues 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = [for qName in queueNames: {
  parent: serviceBusNamespace
  name: qName
  properties: {
    maxDeliveryCount: 10
    deadLetteringOnMessageExpiration: true
    enableBatchedOperations: true
  }
}]

output namespaceId string = serviceBusNamespace.id
output namespaceName string = serviceBusNamespace.name
output endpoint string = serviceBusNamespace.properties.serviceBusEndpoint
output queueNames array = queueNames
