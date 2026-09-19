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

@description('AzureFrontDoor.Backend IP prefixes (CIDR strings) allowed to reach ingress. deploy.sh resolves them at deploy time from `az network list-service-tags --location centralindia` (W-51 section 2.5). Empty by default - see the comment below')
param frontDoorBackendPrefixes array = []

@description('Tags for the resources')
param tags object = {}

// Origin protection (W-51 section 2.5). Front Door Standard has no Private Link origin and
// Container Apps ingress has no header-matching rule, so ipSecurityRestrictions is the only
// control the ingress schema offers. Anything not arriving from a Front Door backend address
// is refused at the ingress; W-51 section 5 step 8 asserts a direct hit returns 403.
//
// DEVIATION FROM THE SPEC, and it is a schema limit rather than a choice. Section 2.5 asks
// for `defaultAction: 'Deny'`; no such property exists on Microsoft.App Ingress in ANY api
// version - `az bicep build` rejects it with BCP037, and ARM would ignore it silently, which
// is worse. Container Apps expresses the same thing differently: the rules must be all-Allow
// or all-Deny, and an all-Allow list IS deny-by-default for every address not listed. So the
// deny is implicit in the list below, not a property.
//
// The trap that follows from that: an EMPTY ipSecurityRestrictions list means NO restriction
// at all - ingress fails OPEN, the opposite of what section 2.5 wants at rest. So when no
// prefixes are supplied - the default, and therefore the state of any checkout that has not
// been through deploy.sh - one Allow rule is emitted for 192.0.2.0/24, the RFC 5737 TEST-NET-1
// range, which no real client can hold. Every real request is then denied, Front Door's
// included. That is DELIBERATE and correct: the perimeter fails closed.
//
// Do not "fix" this by dropping the placeholder, by defaulting the parameter to a real prefix,
// or by emptying the list. Supply the prefixes at deploy time instead. A 403 through Front Door
// means deploy.sh did not resolve the service tag, not that this block is wrong.
//
// The boundary is an IP boundary, not authentication - anything egressing from a Front Door
// backend address satisfies it. X-Azure-FDID validation in application code is W-57.
var noFrontDoorPrefixes = empty(frontDoorBackendPrefixes)
var ingressAllowPrefixes = noFrontDoorPrefixes ? [ '192.0.2.0/24' ] : frontDoorBackendPrefixes

var frontDoorIngressRules = [for (prefix, i) in ingressAllowPrefixes: {
  name: noFrontDoorPrefixes ? 'DenyAllPlaceholder' : 'AllowFrontDoorBackend${i}'
  description: noFrontDoorPrefixes ? 'No Front Door prefixes supplied - unroutable range keeps ingress closed' : 'AzureFrontDoor.Backend service tag prefix, resolved at deploy time'
  ipAddressRange: prefix
  action: 'Allow'
}]

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
        ipSecurityRestrictions: frontDoorIngressRules
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
      // No ingress block, and so no ipSecurityRestrictions: the worker is a queue consumer
      // with no inbound surface at all. W-51 section 3b says "all four ingress blocks", but
      // there are three - this app has never had one (W-50). Nothing to restrict here.
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
        ipSecurityRestrictions: frontDoorIngressRules
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
        ipSecurityRestrictions: frontDoorIngressRules
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
