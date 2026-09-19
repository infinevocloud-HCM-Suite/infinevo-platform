// ─────────────────────────────────────────────────────────────────────────────
// rbac.bicep — the data-plane role assignments of W-51 section 3f.
//
// DEPLOYED TWICE, and it has to be. The matrix straddles two resource groups:
// kv-infinevo-shared is in rg-infinevo-shared and stinfinevo{env} is in rg-infinevo-{env}.
// A role assignment is an extension resource and Bicep will only emit one whose scope
// matches the file's own scope (BCP139), so a single-instance module cannot reach both.
// Rather than split section 3f across two files - which is how it stops being reviewable
// against the spec table - this file is resource-group scoped and main.bicep instantiates
// it once per group, supplying only the target that lives there:
//
//   scope: sharedRg  ->  keyVaultName set,      storageAccountName empty
//   scope: envRg     ->  storageAccountName set, keyVaultName empty
//
// Each block below is guarded on its target being supplied, so an instance emits only the
// assignments that belong at its scope and nothing is duplicated.
//
// EVERY ROLE HERE IS A DATA-PLANE ROLE. None grants control-plane rights: an identity
// holding Storage Blob Data Contributor cannot read the account keys, which is the point -
// W-51 forbids account keys and SAS entirely, so managed identity is the only way in.
//
// THE QUEUE ROLES ARE NOT COVERED BY THE BLOB ROLE. Blob and queue are separate data
// planes with separate role definitions and separate private endpoints; Storage Blob Data
// Contributor grants nothing on a queue. Deleting either queue assignment below breaks
// W-51 section 5 step 6e and nothing else, so it fails late and confusingly - leave both.
// ─────────────────────────────────────────────────────────────────────────────

@description('Target environment name: dev, uat, prod. Used in the role assignment descriptions so an auditor reading the portal can tell which environment granted what.')
param environment string

@description('Name of the shared Key Vault granted to every identity (kv-infinevo-shared). Empty when this instance is deployed at the environment resource group, where the vault does not live.')
param keyVaultName string = ''

@description('Name of the environment storage account carrying both the blob containers and the payrun/import/report queues (stinfinevo{env}). Empty when this instance is deployed at the shared resource group.')
param storageAccountName string = ''

@description('Principal id of id-app-{env}')
param appPrincipalId string

@description('Principal id of id-worker-{env}')
param workerPrincipalId string

@description('Principal id of id-web-{env}')
param webPrincipalId string

@description('Principal id of id-keycloak-{env}')
param keycloakPrincipalId string

@description('Principal id of id-migration-{env}, the identity carried by caj-db-migration-{env}')
param migrationPrincipalId string

// Built-in role definition ids, held as bare guids and resolved with subscriptionResourceId
// below - the same shape as acr-role-assignment.bicep:12.
var keyVaultSecretsUserRoleId = '4633458b-17de-408a-b874-0445c86b69e6'
var storageBlobDataContributorRoleId = 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
var storageQueueDataMessageSenderRoleId = 'c6a89b2d-59bc-44d0-9896-0f6e12d7b80a'
var storageQueueDataMessageProcessorRoleId = '8a0f0c08-91a1-4084-bc3d-661d67233fed'

var hasKeyVault = !empty(keyVaultName)
var hasStorage = !empty(storageAccountName)

// `existing` needs a literal name even when the guard is false, so a placeholder stands in
// for the unused target. Nothing is emitted against it: every resource referencing it is
// conditional on the matching has* flag.
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: hasKeyVault ? keyVaultName : 'placeholder-not-deployed'
}

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' existing = {
  name: hasStorage ? storageAccountName : 'placeholdernotdeployed'
}

// ── Key Vault: Secrets User for all five identities (3f rows 1, 4, 8, 9, 10) ──
// keyvault.bicep:32 sets enableRbacAuthorization: true, so an access policy would do
// nothing - RBAC is the only grant path on this vault.
var keyVaultReaders = [
  { role: 'app', principalId: appPrincipalId }
  { role: 'worker', principalId: workerPrincipalId }
  { role: 'web', principalId: webPrincipalId }
  { role: 'keycloak', principalId: keycloakPrincipalId }
  { role: 'migration', principalId: migrationPrincipalId }
]

resource keyVaultSecretsUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for reader in (hasKeyVault ? keyVaultReaders : []): {
  name: guid(keyVault.id, reader.principalId, keyVaultSecretsUserRoleId)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', keyVaultSecretsUserRoleId)
    principalId: reader.principalId
    // Stated explicitly because a freshly created user-assigned identity may not have
    // replicated into Microsoft Graph when the assignment is submitted; left unset, ARM
    // infers the type by lookup and fails intermittently on a first deployment.
    principalType: 'ServicePrincipal'
    description: 'W-51 3f: id-${reader.role}-${environment} reads secrets from ${keyVaultName}'
  }
}]

// ── Storage blob: Contributor for app and worker (3f rows 2, 5) ─────────────
var blobContributors = [
  { role: 'app', principalId: appPrincipalId }
  { role: 'worker', principalId: workerPrincipalId }
]

resource storageBlobDataContributor 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for principal in (hasStorage ? blobContributors : []): {
  name: guid(storageAccount.id, principal.principalId, storageBlobDataContributorRoleId)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageBlobDataContributorRoleId)
    principalId: principal.principalId
    principalType: 'ServicePrincipal'
    description: 'W-51 3f: id-${principal.role}-${environment} reads and writes blobs in ${storageAccountName}'
  }
}]

// ── Storage queue: Sender for app and worker (3f rows 3, 7) ─────────────────
// The worker is a sender as well as a processor: a failed payrun message is put back by
// the worker itself, and the processor role alone cannot re-queue one.
var queueSenders = [
  { role: 'app', principalId: appPrincipalId }
  { role: 'worker', principalId: workerPrincipalId }
]

resource storageQueueDataMessageSender 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for principal in (hasStorage ? queueSenders : []): {
  name: guid(storageAccount.id, principal.principalId, storageQueueDataMessageSenderRoleId)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageQueueDataMessageSenderRoleId)
    principalId: principal.principalId
    principalType: 'ServicePrincipal'
    description: 'W-51 3f: id-${principal.role}-${environment} enqueues messages on ${storageAccountName}'
  }
}]

// ── Storage queue: Processor for the worker only (3f row 6) ─────────────────
// Peek + delete, i.e. consume. Deliberately NOT granted to id-app: the app enqueues work
// and must not be able to consume it, which is the separation W-52 is built on.
resource storageQueueDataMessageProcessor 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (hasStorage) {
  name: guid(storageAccount.id, workerPrincipalId, storageQueueDataMessageProcessorRoleId)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageQueueDataMessageProcessorRoleId)
    principalId: workerPrincipalId
    principalType: 'ServicePrincipal'
    description: 'W-51 3f: id-worker-${environment} consumes messages from ${storageAccountName}'
  }
}
