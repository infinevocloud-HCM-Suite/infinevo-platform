@description('Environment name: dev, uat, prod')
param environment string

@description('Azure region')
param location string = resourceGroup().location

@description('Container Apps Environment ID')
param environmentId string

@description('ACR login server (e.g. crinfinevo.azurecr.io)')
param acrLoginServer string = 'crinfinevo.azurecr.io'

@description('Default starter container image')
param starterImage string = 'mcr.microsoft.com/k8se/quickstart:latest'

@description('CPU allocation per replica (e.g. 0.25, 0.5)')
param cpu string = '0.25'

@description('Memory allocation per replica (e.g. 0.5Gi, 1.0Gi)')
param memory string = '0.5Gi'

@description('Minimum replicas (0 for scale-to-zero in dev/uat, 2 for prod)')
param minReplicas int = 0

@description('Maximum replicas')
param maxReplicas int = 3

@description('Managed identity IDs and client IDs per role')
param identities object

@description('Tags for the resources')
param tags object = {}

// 1. Container App: app (Backend API)
resource appContainerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'ca-infinevo-${environment}-app'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identities.app.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
      registries: [
        {
          server: acrLoginServer
          identity: identities.app.id
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'app'
          image: starterImage
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/'
                port: 8080
              }
              initialDelaySeconds: 10
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
      }
    }
  }
}

// 2. Container App: worker (Background Queue Processor, No Ingress)
resource workerContainerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'ca-infinevo-${environment}-worker'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identities.worker.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      registries: [
        {
          server: acrLoginServer
          identity: identities.worker.id
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'worker'
          image: starterImage
          resources: {
            cpu: json(cpu)
            memory: memory
          }
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
      }
    }
  }
}

// 3. Container App: web (Frontend SPA)
resource webContainerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'ca-infinevo-${environment}-web'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identities.web.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 80
        transport: 'auto'
        allowInsecure: false
      }
      registries: [
        {
          server: acrLoginServer
          identity: identities.web.id
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'web'
          image: starterImage
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/'
                port: 80
              }
              initialDelaySeconds: 5
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
      }
    }
  }
}

// 4. Container App: keycloak (Identity Provider)
resource keycloakContainerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'ca-infinevo-${environment}-keycloak'
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identities.keycloak.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
      registries: [
        {
          server: acrLoginServer
          identity: identities.keycloak.id
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'keycloak'
          image: starterImage
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/'
                port: 8080
              }
              initialDelaySeconds: 15
              periodSeconds: 20
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
      }
    }
  }
}

output appName string = appContainerApp.name
output appId string = appContainerApp.id
output appFqdn string = appContainerApp.properties.configuration.ingress.fqdn

output workerName string = workerContainerApp.name
output workerId string = workerContainerApp.id

output webName string = webContainerApp.name
output webId string = webContainerApp.id
output webFqdn string = webContainerApp.properties.configuration.ingress.fqdn

output keycloakName string = keycloakContainerApp.name
output keycloakId string = keycloakContainerApp.id
output keycloakFqdn string = keycloakContainerApp.properties.configuration.ingress.fqdn
