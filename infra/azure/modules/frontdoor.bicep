@description('Name of the shared Front Door profile. One profile serves dev, uat and prod (W-51 section 2.4)')
param profileName string = 'afd-infinevo-shared'

@description('Environment name: dev, uat, prod. Every per-environment resource below is named from it')
param environment string

@description('Ingress FQDN of ca-infinevo-{env}-web, the origin behind /*')
param webOriginHostName string

@description('Ingress FQDN of ca-infinevo-{env}-app, the origin behind /api/*')
param appOriginHostName string

@description('Ingress FQDN of ca-infinevo-{env}-keycloak, the origin behind /auth/*')
param keycloakOriginHostName string

@description('Requests per client IP per minute above which the WAF blocks (W-51 section 2.4)')
param rateLimitThreshold int = 100

@description('Tags for the resources')
param tags object = {}

// Front Door is a global service: the profile, its endpoints and the WAF policy are all
// location 'global'. That is not a D-18 residency breach and W-51 section 5 step 7
// excludes it explicitly - no payroll data is stored at the edge, it is proxied.
var globalLocation = 'global'

// The profile is declared by every environment's deployment with identical properties, so
// whichever environment deploys first creates it and the others are no-ops. Only the child
// resources below are per-environment, which is what lets `deploy.sh --env uat` add an
// endpoint without disturbing dev.
//
// Standard, not Premium (W-51 section 3d): ~$35/month against ~$330, and D-19 scale is
// 10 tenants x 100 employees. What that gives up is the managed OWASP rule set and Private
// Link origins - hence the custom WAF rules below and ipSecurityRestrictions on the
// Container Apps ingress (containerapps.bicep) instead.
resource profile 'Microsoft.Cdn/profiles@2024-02-01' = {
  name: profileName
  location: globalLocation
  tags: tags
  sku: {
    name: 'Standard_AzureFrontDoor'
  }
  properties: {
    originResponseTimeoutSeconds: 60
  }
}

// ── Endpoint ─────────────────────────────────────────────────────────────────
// The name is load-bearing: W-51 section 5 step 4 queries `--endpoint-name
// ep-infinevo-${ENV}` verbatim. Azure appends a hash to the hostname, so the public name
// is ep-infinevo-dev-<hash>.z01.azurefd.net and is read from the hostName output.
//
// No custom domains and no DNS zone. Founder decision 2026-09-19 (W-51 decision 1): DNS
// stays at the external registrar, so binding dev|uat|app.infinevo.cloud is a manual pair
// of records there and changes no Bicep. Everything in section 5 runs on *.azurefd.net.
resource endpoint 'Microsoft.Cdn/profiles/afdEndpoints@2024-02-01' = {
  parent: profile
  name: 'ep-infinevo-${environment}'
  location: globalLocation
  tags: tags
  properties: {
    enabledState: 'Enabled'
  }
}

// ── Origin groups ────────────────────────────────────────────────────────────
// Three per environment, named og-{env}-{role}. The names are load-bearing too: section 5
// step 4b runs `az afd origin list --origin-group-name og-${ENV}-${want}` and greps for the
// Container App's live ingress FQDN, which is what catches a route wired to the wrong app.
//
// The health probe uses HEAD on '/' rather than a health path on purpose:
// 05-azure-architecture.md:159 keeps health and readiness endpoints off the Front Door
// path, and the starter image serves '/' (containerapps.bicep starterImage).
var probeSettings = {
  probePath: '/'
  probeRequestType: 'HEAD'
  probeProtocol: 'Https'
  // 100s, the Standard-tier minimum for the shared profile. A shorter interval multiplies
  // probe traffic against an ingress that is already IP-restricted.
  probeIntervalInSeconds: 100
}

var loadBalancingSettings = {
  sampleSize: 4
  successfulSamplesRequired: 3
  additionalLatencyInMilliseconds: 50
}

resource webOriginGroup 'Microsoft.Cdn/profiles/originGroups@2024-02-01' = {
  parent: profile
  name: 'og-${environment}-web'
  properties: {
    loadBalancingSettings: loadBalancingSettings
    healthProbeSettings: probeSettings
    sessionAffinityState: 'Disabled'
  }
}

resource appOriginGroup 'Microsoft.Cdn/profiles/originGroups@2024-02-01' = {
  parent: profile
  name: 'og-${environment}-app'
  properties: {
    loadBalancingSettings: loadBalancingSettings
    healthProbeSettings: probeSettings
    sessionAffinityState: 'Disabled'
  }
}

resource keycloakOriginGroup 'Microsoft.Cdn/profiles/originGroups@2024-02-01' = {
  parent: profile
  name: 'og-${environment}-keycloak'
  properties: {
    loadBalancingSettings: loadBalancingSettings
    healthProbeSettings: probeSettings
    sessionAffinityState: 'Disabled'
  }
}

// ── Origins ──────────────────────────────────────────────────────────────────
// originHostHeader is set to the Container App FQDN, not left to default: Container Apps
// routes by Host header, so forwarding the *.azurefd.net host would land on the
// environment's default 404 rather than the app. enforceCertificateNameCheck therefore
// also holds - the certificate presented matches the name we send.
resource webOrigin 'Microsoft.Cdn/profiles/originGroups/origins@2024-02-01' = {
  parent: webOriginGroup
  name: 'origin-${environment}-web'
  properties: {
    hostName: webOriginHostName
    originHostHeader: webOriginHostName
    httpPort: 80
    httpsPort: 443
    priority: 1
    weight: 1000
    enabledState: 'Enabled'
    enforceCertificateNameCheck: true
  }
}

resource appOrigin 'Microsoft.Cdn/profiles/originGroups/origins@2024-02-01' = {
  parent: appOriginGroup
  name: 'origin-${environment}-app'
  properties: {
    hostName: appOriginHostName
    originHostHeader: appOriginHostName
    httpPort: 80
    httpsPort: 443
    priority: 1
    weight: 1000
    enabledState: 'Enabled'
    enforceCertificateNameCheck: true
  }
}

resource keycloakOrigin 'Microsoft.Cdn/profiles/originGroups/origins@2024-02-01' = {
  parent: keycloakOriginGroup
  name: 'origin-${environment}-keycloak'
  properties: {
    hostName: keycloakOriginHostName
    originHostHeader: keycloakOriginHostName
    httpPort: 80
    httpsPort: 443
    priority: 1
    weight: 1000
    enabledState: 'Enabled'
    enforceCertificateNameCheck: true
  }
}

// ── Rule set: origin tagging ─────────────────────────────────────────────────
// W-51 section 2.4 calls this rule set `rs-origin-tag`. DEVIATION, and it is a platform
// constraint rather than a choice: Microsoft.Cdn rule set and rule names are restricted to
// letters and digits - a hyphen is rejected at deployment time, not at build time. The
// resource is therefore `rsorigintag`; nothing in section 5 reads the rule set name, only
// the header it stamps.
//
// Why it exists at all: all three origins run the same starter image and return the same
// body, so `curl https://.../api/` looks identical whether it reached the app or the web
// origin. This stamps X-Infinevo-Origin on the response, which is what section 5 step 4b
// compares - and what deliberate break 5 (repoint a route at the wrong origin group)
// relies on to fail.
//
// Rules run in order and each matching rule applies, with headerAction 'Overwrite'. So the
// unconditional 'web' rule runs first as the default and the two path rules that follow
// override it. Order matters: reversing them tags everything 'web'.
resource originTagRuleSet 'Microsoft.Cdn/profiles/ruleSets@2024-02-01' = {
  parent: profile
  name: 'rsorigintag'
}

resource tagWebRule 'Microsoft.Cdn/profiles/ruleSets/rules@2024-02-01' = {
  parent: originTagRuleSet
  name: 'tagweb'
  properties: {
    order: 1
    // No conditions: this is the fall-through that matches /* and is then overridden by
    // the two rules below when the path is /api/ or /auth/.
    conditions: []
    actions: [
      {
        name: 'ModifyResponseHeader'
        parameters: {
          typeName: 'DeliveryRuleHeaderActionParameters'
          headerAction: 'Overwrite'
          headerName: 'X-Infinevo-Origin'
          value: 'web'
        }
      }
    ]
    matchProcessingBehavior: 'Continue'
  }
}

// Both '/api/' and 'api/' are listed as match values. Front Door normalises the URL path
// before evaluating UrlPath conditions and the presence of the leading slash has differed
// between rules-engine versions; matching either form makes the tag deterministic, which is
// the whole point of the rule set.
resource tagAppRule 'Microsoft.Cdn/profiles/ruleSets/rules@2024-02-01' = {
  parent: originTagRuleSet
  name: 'tagapp'
  properties: {
    order: 2
    conditions: [
      {
        name: 'UrlPath'
        parameters: {
          typeName: 'DeliveryRuleUrlPathMatchConditionParameters'
          operator: 'BeginsWith'
          negateCondition: false
          matchValues: [
            '/api/'
            'api/'
          ]
          transforms: [
            'Lowercase'
          ]
        }
      }
    ]
    actions: [
      {
        name: 'ModifyResponseHeader'
        parameters: {
          typeName: 'DeliveryRuleHeaderActionParameters'
          headerAction: 'Overwrite'
          headerName: 'X-Infinevo-Origin'
          value: 'app'
        }
      }
    ]
    matchProcessingBehavior: 'Continue'
  }
  dependsOn: [
    tagWebRule
  ]
}

resource tagKeycloakRule 'Microsoft.Cdn/profiles/ruleSets/rules@2024-02-01' = {
  parent: originTagRuleSet
  name: 'tagkeycloak'
  properties: {
    order: 3
    conditions: [
      {
        name: 'UrlPath'
        parameters: {
          typeName: 'DeliveryRuleUrlPathMatchConditionParameters'
          operator: 'BeginsWith'
          negateCondition: false
          matchValues: [
            '/auth/'
            'auth/'
          ]
          transforms: [
            'Lowercase'
          ]
        }
      }
    ]
    actions: [
      {
        name: 'ModifyResponseHeader'
        parameters: {
          typeName: 'DeliveryRuleHeaderActionParameters'
          headerAction: 'Overwrite'
          headerName: 'X-Infinevo-Origin'
          value: 'keycloak'
        }
      }
    ]
    matchProcessingBehavior: 'Continue'
  }
  dependsOn: [
    tagAppRule
  ]
}

// ── Routes ───────────────────────────────────────────────────────────────────
// /api/* and /auth/* are the specific patterns and /* is the catch-all; Front Door matches
// the most specific pattern, so declaration order does not decide routing. The routes are
// chained with dependsOn all the same: the control plane rejects concurrent writes to the
// same endpoint's route collection, and parallel deployment is the default.
//
// linkToDefaultDomain 'Enabled' is what puts these routes on *.azurefd.net - with no custom
// domain (decision 1) it is the only way the route is reachable at all.
// forwardingProtocol 'HttpsOnly' keeps the Front Door-to-origin hop encrypted; the Container
// Apps ingress sets allowInsecure: false and would refuse plain HTTP anyway.
resource webRoute 'Microsoft.Cdn/profiles/afdEndpoints/routes@2024-02-01' = {
  parent: endpoint
  name: 'route-${environment}-web'
  properties: {
    originGroup: {
      id: webOriginGroup.id
    }
    supportedProtocols: [
      'Http'
      'Https'
    ]
    patternsToMatch: [
      '/*'
    ]
    forwardingProtocol: 'HttpsOnly'
    linkToDefaultDomain: 'Enabled'
    httpsRedirect: 'Enabled'
    enabledState: 'Enabled'
    ruleSets: [
      {
        id: originTagRuleSet.id
      }
    ]
  }
  dependsOn: [
    webOrigin
    tagKeycloakRule
  ]
}

resource appRoute 'Microsoft.Cdn/profiles/afdEndpoints/routes@2024-02-01' = {
  parent: endpoint
  name: 'route-${environment}-app'
  properties: {
    originGroup: {
      id: appOriginGroup.id
    }
    supportedProtocols: [
      'Http'
      'Https'
    ]
    patternsToMatch: [
      '/api/*'
    ]
    forwardingProtocol: 'HttpsOnly'
    linkToDefaultDomain: 'Enabled'
    httpsRedirect: 'Enabled'
    enabledState: 'Enabled'
    ruleSets: [
      {
        id: originTagRuleSet.id
      }
    ]
  }
  dependsOn: [
    appOrigin
    webRoute
  ]
}

resource keycloakRoute 'Microsoft.Cdn/profiles/afdEndpoints/routes@2024-02-01' = {
  parent: endpoint
  name: 'route-${environment}-keycloak'
  properties: {
    originGroup: {
      id: keycloakOriginGroup.id
    }
    supportedProtocols: [
      'Http'
      'Https'
    ]
    patternsToMatch: [
      '/auth/*'
    ]
    forwardingProtocol: 'HttpsOnly'
    linkToDefaultDomain: 'Enabled'
    httpsRedirect: 'Enabled'
    enabledState: 'Enabled'
    ruleSets: [
      {
        id: originTagRuleSet.id
      }
    ]
  }
  dependsOn: [
    keycloakOrigin
    appRoute
  ]
}

// ── WAF ──────────────────────────────────────────────────────────────────────
// One shared policy, like the profile: WAF policy names permit no hyphen or underscore,
// which is why W-51 section 2.4 spells it `wafinfinevoshared`.
//
// Mode 'Prevention', not 'Detection' - Detection logs and forwards, so section 5 step 4c
// would see 200 and deliberate break 4 could not fail.
//
// Managed rule sets (the OWASP Default Rule Set) are Premium-only and are not attempted;
// section 3d records that as the cost trade and W-64 re-tests it.
resource wafPolicy 'Microsoft.Network/frontDoorWebApplicationFirewallPolicies@2024-02-01' = {
  name: 'wafinfinevoshared'
  // 'Global' is the only value this resource type accepts, and section 5 step 7 excludes it
  // from the centralindia residency assertion.
  location: 'Global'
  tags: tags
  sku: {
    name: 'Standard_AzureFrontDoor'
  }
  properties: {
    policySettings: {
      enabledState: 'Enabled'
      mode: 'Prevention'
      requestBodyCheck: 'Enabled'
    }
    customRules: {
      rules: [
        // The canary. It exists so WAF enforcement is provable in one request rather than
        // inferred from configuration: section 5 step 4c curls /api/?wafcanary=block and
        // requires 403. Lower priority number means evaluated first, so a canary request
        // is blocked regardless of the rate-limit state.
        //
        // It is not a security control and blocks nothing an attacker would send. Removing
        // it silently disarms deliberate break 4, so it stays.
        {
          name: 'wafcanaryblock'
          priority: 10
          enabledState: 'Enabled'
          ruleType: 'MatchRule'
          action: 'Block'
          matchConditions: [
            {
              matchVariable: 'QueryString'
              operator: 'Contains'
              negateCondition: false
              matchValue: [
                'wafcanary=block'
              ]
              transforms: [
                'Lowercase'
              ]
            }
          ]
        }
        // The protection that actually matters. RateLimitRule groups by client IP
        // implicitly; the RemoteAddr condition below is how the rule is made to match every
        // client - an AFD custom rule requires at least one match condition, and
        // 0.0.0.0/0 plus ::/0 is the way to express "all of them".
        {
          name: 'ratelimitperclientip'
          priority: 100
          enabledState: 'Enabled'
          ruleType: 'RateLimitRule'
          rateLimitDurationInMinutes: 1
          rateLimitThreshold: rateLimitThreshold
          action: 'Block'
          matchConditions: [
            {
              matchVariable: 'RemoteAddr'
              operator: 'IPMatch'
              negateCondition: false
              matchValue: [
                '0.0.0.0/0'
                '::/0'
              ]
            }
          ]
        }
      ]
    }
  }
}

// The association is the part that makes the policy take effect; a WAF policy with no
// security policy pointing at an endpoint enforces nothing. Deliberate break 4 deletes this
// resource and expects section 5 step 4c to return 200.
//
// One per environment rather than one listing all three endpoints: this module is deployed
// once per environment and can only see its own endpoint, and a per-environment policy is
// what lets teardown.sh remove dev's association without touching uat's.
resource securityPolicy 'Microsoft.Cdn/profiles/securityPolicies@2024-02-01' = {
  parent: profile
  name: 'sp-infinevo-${environment}'
  properties: {
    parameters: {
      type: 'WebApplicationFirewall'
      wafPolicy: {
        id: wafPolicy.id
      }
      associations: [
        {
          domains: [
            {
              id: endpoint.id
            }
          ]
          patternsToMatch: [
            '/*'
          ]
        }
      ]
    }
  }
}

output profileName string = profile.name
output endpointName string = endpoint.name
output endpointHostName string = endpoint.properties.hostName
output wafPolicyName string = wafPolicy.name
output securityPolicyName string = securityPolicy.name
