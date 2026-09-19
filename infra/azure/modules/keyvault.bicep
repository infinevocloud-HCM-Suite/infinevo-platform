@description('Name of the Key Vault')
param keyVaultName string = 'kv-infinevo-shared'

@description('Azure region for Key Vault')
param location string = resourceGroup().location

@description('Whether purge protection is enabled')
param enablePurgeProtection bool = false

@description('Object id of the principal running the deployment. Granted Key Vault Secrets Officer so deploy.sh and post-deploy-db.sh can write secrets to an RBAC-authorised vault (review F-5). Empty skips the assignment.')
param deployerObjectId string = ''

@description('Transient IPv4 rules allowed through the network ACL, each an object with a value property holding a CIDR such as a single /32. Empty at rest; deploy.sh adds its own egress address for the length of one run and revokes it on exit (W-51 section 2.3). Section 5 step 2 fails if any rule survives.')
param allowedIpRules array = []

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
    // Stays 'Enabled' on purpose, and is the one resource in W-51 that is not Disabled.
    // deploy.sh:120 and post-deploy-db.sh:58,106,112 are Key Vault DATA-PLANE calls made
    // from CI before anything exists inside the VNet; Disabled kills the endpoint outright
    // and no ACL rule can reopen it, so the deployment that creates the vault could never
    // write to it. The vault is closed by networkAcls below instead - defaultAction Deny
    // leaves no open public surface at rest (W-51 section 2.3, founder-confirmed 2026-09-19).
    publicNetworkAccess: 'Enabled'
    networkAcls: {
      // AzureServices bypass covers the platform's own trusted services; the private
      // endpoint in snet-pe is unaffected by these rules either way.
      bypass: 'AzureServices'
      defaultAction: 'Deny'
      ipRules: allowedIpRules
      virtualNetworkRules: []
    }
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
