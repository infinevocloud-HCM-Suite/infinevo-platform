// ─────────────────────────────────────────────────────────────────────────────
// ai-openai.bicep — the Azure side of the AI helper (W-114.2).
//
// One Azure OpenAI account, one GPT deployment, one role assignment and one budget.
// main.bicep instantiates this module only when deployAi is true, so with the switch off
// (the default) none of it exists.
//
// THE REGION IS ITS OWN PARAMETER, NOT main.bicep's `location`. `location` is locked to
// centralindia (D-18), and Azure OpenAI's India models are offered in South India, not
// Central India. South India is in the India geography, so data at rest stays in India
// (W-114.2 section 3.1, choices 1 and 2). A region outside India is choice 3 and needs
// the founder's word (D-61 does not cover it).
//
// NO KEYS. disableLocalAuth is true, so the account has no usable API key to leak; the app
// signs in with its user-assigned identity, which is granted Cognitive Services OpenAI
// User below. The worker never calls the model and gets no role.
//
// NO PUBLIC ADDRESS. publicNetworkAccess is Disabled; main.bicep adds a private endpoint
// in snet-pe (Central India) and the privatelink.openai.azure.com zone resolves the
// account's host name to it.
// ─────────────────────────────────────────────────────────────────────────────

@description('Environment name: dev, uat, prod')
param environment string

@description('Region of the Azure OpenAI account. Separate from main.bicep `location` (W-114.2 section 3.1)')
param aiLocation string

@description('Model name, e.g. gpt-4.1-mini')
param aiModelName string

@description('Model version, e.g. 2025-04-14')
param aiModelVersion string

@description('Deployment type: Standard keeps processing in the resource geography; DataZoneStandard in a Microsoft data zone; GlobalStandard anywhere the model runs')
@allowed([
  'Standard'
  'DataZoneStandard'
  'GlobalStandard'
])
param aiDeploymentSku string

@description('Deployment capacity in units of 1,000 tokens per minute. Must fit inside the subscription quota for this model, type and region')
@minValue(1)
param aiCapacity int

@description('Principal id of id-app-{env}, the only identity granted the model')
param appPrincipalId string

@description('Mail address the budget notifications go to')
param alertEmail string

@description('Monthly budget for this account, whole units of the billing account currency')
@minValue(1)
param aiMonthlyBudget int

@description('First day of the month the budget starts counting, yyyy-MM-dd. Fixed rather than utcNow() so a later redeploy does not try to move it')
param aiBudgetStartDate string

@description('Tags for the resources')
param tags object = {}

var accountName = 'oai-infinevo-${environment}'
var deploymentName = aiModelName

// Built-in role "Cognitive Services OpenAI User", resolved with subscriptionResourceId
// below - the same shape as rbac.bicep:51.
var openAiUserRoleId = '5e0bd9bd-7b93-4f28-af87-19fc36ad61bd'

resource account 'Microsoft.CognitiveServices/accounts@2026-07-01' = {
  name: accountName
  location: aiLocation
  tags: tags
  kind: 'OpenAI'
  sku: {
    name: 'S0'
  }
  properties: {
    // The subdomain is what gives the account the https://<name>.openai.azure.com host
    // that the private DNS zone and Entra sign-in both need. It is globally unique.
    customSubDomainName: accountName
    publicNetworkAccess: 'Disabled'
    disableLocalAuth: true
    networkAcls: {
      defaultAction: 'Deny'
    }
  }
}

resource deployment 'Microsoft.CognitiveServices/accounts/deployments@2026-07-01' = {
  parent: account
  name: deploymentName
  sku: {
    name: aiDeploymentSku
    capacity: aiCapacity
  }
  properties: {
    model: {
      format: 'OpenAI'
      name: aiModelName
      version: aiModelVersion
    }
    // Stays on the pinned version until Microsoft retires it, then moves to the default.
    versionUpgradeOption: 'OnceCurrentVersionExpired'
  }
}

resource appOpenAiUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(account.id, appPrincipalId, openAiUserRoleId)
  scope: account
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', openAiUserRoleId)
    principalId: appPrincipalId
    // Stated for the same reason as rbac.bicep:88-91: a new identity may not have
    // replicated into Graph yet, and ARM's type lookup then fails intermittently.
    principalType: 'ServicePrincipal'
    description: 'W-114.2: id-app-${environment} calls the model on ${accountName}'
  }
}

// A second, Azure-side guard beside the per-customer cap of W-114.1. It only sends mail;
// a budget never stops a resource.
resource budget 'Microsoft.Consumption/budgets@2024-08-01' = {
  name: 'budget-${accountName}'
  properties: {
    category: 'Cost'
    amount: aiMonthlyBudget
    timeGrain: 'Monthly'
    timePeriod: {
      startDate: aiBudgetStartDate
    }
    filter: {
      dimensions: {
        name: 'ResourceId'
        operator: 'In'
        values: [
          account.id
        ]
      }
    }
    notifications: {
      actual80: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 80
        thresholdType: 'Actual'
        contactEmails: [
          alertEmail
        ]
      }
      actual100: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Actual'
        contactEmails: [
          alertEmail
        ]
      }
    }
  }
}

output accountId string = account.id
output accountName string = account.name
// properties.endpoint, not a hand-built host: for kind OpenAI with a custom subdomain it is
// https://<name>.openai.azure.com/ and the linter forbids hard-coded cloud host names.
output endpoint string = account.properties.endpoint
output deploymentName string = deployment.name
