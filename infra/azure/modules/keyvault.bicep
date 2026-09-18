@description('Name of the Key Vault')
param keyVaultName string = 'kv-infinevo-shared'

@description('Azure region for Key Vault')
param location string = resourceGroup().location

@description('Whether purge protection is enabled')
param enablePurgeProtection bool = false

@description('Object id of the principal running the deployment. Granted Key Vault Secrets Officer so deploy.sh and post-deploy-db.sh can write secrets to an RBAC-authorised vault (review F-5). Empty skips the assignment.')
param deployerObjectId string = ''

@description('Tags for the resource')
param tags object = {}

// Key Vault Secrets Officer - get/set/list/delete secrets, no key or certificate rights.
var secretsOfficerRoleId = 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  tags: tags
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    enablePurgeProtection: enablePurgeProtection ? true : null
    publicNetworkAccess: 'Enabled'
  }
}

// Without this the vault is RBAC-authorised and nobody can write to it: `az keyvault
// secret set` returns 403 after every resource has already been created (review F-5).
resource deployerSecretsOfficer 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(deployerObjectId)) {
  name: guid(keyVault.id, deployerObjectId, secretsOfficerRoleId)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', secretsOfficerRoleId)
    principalId: deployerObjectId
  }
}

output keyVaultId string = keyVault.id
output keyVaultName string = keyVault.name
output keyVaultUri string = keyVault.properties.vaultUri
