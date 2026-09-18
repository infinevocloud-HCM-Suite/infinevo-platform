@description('Target environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Tags for the resources')
param tags object = {}

var roles = [
  'app'
  'worker'
  'web'
  'keycloak'
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

output identities array = [for (role, i) in roles: {
  role: role
  id: identities[i].id
  principalId: identities[i].properties.principalId
  clientId: identities[i].properties.clientId
  name: identities[i].name
}]
