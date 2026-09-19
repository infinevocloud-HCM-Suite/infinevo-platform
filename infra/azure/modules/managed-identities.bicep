@description('Target environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Tags for the resources')
param tags object = {}

// W-51 T4 adds 'migration'. id-migration-{env} is the identity carried by
// caj-db-migration-{env}: it pulls the runner image from crinfinevo (AcrPull) and reads
// psql-admin-pw from kv-infinevo-shared (Key Vault Secrets User) over the vault private
// endpoint. APPEND ONLY - the outputs below index this array positionally, so reordering
// it silently re-points every downstream role assignment.
var roles = [
  'app'
  'worker'
  'web'
  'keycloak'
  'migration'
]

resource identities 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = [for role in roles: {
  name: 'id-${role}-${environment}'
  location: location
  tags: tags
}]

output appIdentityId string = identities[0].id
output appIdentityPrincipalId string = identities[0].properties.principalId
output appIdentityClientId string = identities[0].properties.clientId

output workerIdentityId string = identities[1].id
output workerIdentityPrincipalId string = identities[1].properties.principalId
output workerIdentityClientId string = identities[1].properties.clientId

output webIdentityId string = identities[2].id
output webIdentityPrincipalId string = identities[2].properties.principalId
output webIdentityClientId string = identities[2].properties.clientId

output keycloakIdentityId string = identities[3].id
output keycloakIdentityPrincipalId string = identities[3].properties.principalId
output keycloakIdentityClientId string = identities[3].properties.clientId

output migrationIdentityId string = identities[4].id
output migrationIdentityPrincipalId string = identities[4].properties.principalId
output migrationIdentityClientId string = identities[4].properties.clientId

output identities array = [for (role, i) in roles: {
  role: role
  id: identities[i].id
  principalId: identities[i].properties.principalId
  clientId: identities[i].properties.clientId
  name: identities[i].name
}]
