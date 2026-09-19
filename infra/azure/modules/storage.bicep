@description('Name of the Storage Account (alphanumeric lowercase, max 24 chars)')
param storageAccountName string

@description('Azure region')
param location string = resourceGroup().location

@description('Storage Account SKU')
@allowed([
  'Standard_LRS'
  'Standard_ZRS'
  'Standard_GRS'
])
param skuName string = 'Standard_LRS'

@description('Tags for the resource')
param tags object = {}

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageAccountName
  location: location
  tags: tags
  kind: 'StorageV2'
  sku: {
    name: skuName
  }
  properties: {
    allowBlobPublicAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    accessTier: 'Hot'
    // W-51 section 2.3: reached only through the blob and queue private endpoints in
    // snet-pe. Blob and queue are separate private link sub-resources and each needs its
    // own endpoint - one does not cover the other.
    publicNetworkAccess: 'Disabled'
    encryption: {
      services: {
        blob: {
          enabled: true
          keyType: 'Account'
        }
      }
      keySource: 'Microsoft.Storage'
    }
    // networkAcls are inert while publicNetworkAccess is Disabled - that switch closes
    // the endpoint ahead of any rule evaluation. Left as-is so that re-enabling public
    // access is a one-property change with an auditable diff.
    networkAcls: {
      bypass: 'AzureServices'
      defaultAction: 'Allow'
    }
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
  properties: {
    deleteRetentionPolicy: {
      enabled: true
      days: 7
    }
  }
}

var containerNames = [
  'documents'
  'payslips'
  'proofs'
]

resource containers 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = [for cName in containerNames: {
  parent: blobService
  name: cName
  properties: {
    publicAccess: 'None'
  }
}]

// ── Queues ───────────────────────────────────────────────────────────────────
// Azure Storage Queues, NOT Service Bus. Founder decision 2026-09-19: private endpoints
// on Service Bus are Premium-tier only (~10x Standard), which D-19 scale does not justify,
// whereas Storage Queue supports a private endpoint on this Standard account at no extra
// cost. That is what lets the account satisfy the rule in
// docs/target-state/05-azure-architecture.md:75 - "Only Front Door is public" - instead of
// recording a deviation from it. The three names match the queues W-50 created on the
// Service Bus namespace it replaces (W-50 spec section "In scope").
//
// ACCESS IS BY MANAGED IDENTITY ONLY. No account key or SAS is read, stored or emitted by
// this module. The two data-plane roles the queues need are assigned in T4 (W-51 section
// 3f), scoped to this account:
//   Storage Queue Data Message Sender     -> id-app-{env}     (enqueues work)
//   Storage Queue Data Message Processor  -> id-worker-{env}  (peek + delete, i.e. consume)
// Neither is covered by Storage Blob Data Contributor; queue and blob are separate
// data planes with separate role definitions.
resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
}

var queueNames = [
  'payrun'
  'import'
  'report'
]

resource queues 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-01-01' = [for qName in queueNames: {
  parent: queueService
  name: qName
}]

output storageAccountId string = storageAccount.id
output storageAccountName string = storageAccount.name
output primaryBlobEndpoint string = storageAccount.properties.primaryEndpoints.blob
output primaryQueueEndpoint string = storageAccount.properties.primaryEndpoints.queue
output containerNames array = containerNames
output queueNames array = queueNames
