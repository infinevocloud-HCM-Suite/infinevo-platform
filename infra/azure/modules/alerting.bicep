@description('Deployment environment: dev, uat, prod')
param environment string

@description('Azure region for regional alert rules (Central India)')
param location string = resourceGroup().location

@description('Resource ID of the Log Analytics workspace')
param workspaceId string

@description('Resource ID of the PostgreSQL Flexible Server')
param postgresServerId string

@description('Primary on-call notification email address (W-61 decision 1: alerts@infinevocloud.com)')
param alertEmail string

@description('Tags for the resources')
param tags object = {}

// ── 1. Action Group: ag-infinevo-{env}-oncall ──────────────────────────────────
// Action Groups in Azure Monitor use location 'global'
resource actionGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: 'ag-infinevo-${environment}-oncall'
  location: 'global'
  tags: tags
  properties: {
    groupShortName: 'infinevo-ops'
    enabled: true
    emailReceivers: empty(alertEmail) ? [] : [
      {
        name: 'oncall-team'
        emailAddress: alertEmail
        useCommonAlertSchema: true
      }
    ]
  }
}

// ── 2. Critical Business Alert: Failed Pay Run (Sev-1) ─────────────────────────
// Evaluates every 5m over 15m sliding window. Fires immediately if worker logs job failure.
resource alertPayrunFailure 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-payrun-failure-${environment}'
  location: location
  tags: tags
  properties: {
    displayName: 'Critical: Worker Pay Run Job Failure (${environment})'
    description: 'Fires when a background pay run throws an exception or marks job as failed in worker (W-61 PLAT-08)'
    severity: 1
    enabled: true
    scopes: [
      workspaceId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      allOf: [
        {
          query: 'ContainerAppConsoleLogs_CL | where ContainerAppName_s startswith "ca-infinevo-" and ContainerAppName_s endswith "-worker" | where Log_s has "Failed to process payrun job" or (Log_s has "payrun" and Log_s has "markFailed")'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 0
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
  }
}

// ── 3. High Severity: Container Apps HTTP 5xx Spikes (Sev-2) ───────────────────
resource alert5xxSpikes 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-5xx-spikes-${environment}'
  location: location
  tags: tags
  properties: {
    displayName: 'High: HTTP 5xx Server Error Spikes (${environment})'
    description: 'Fires when 5xx errors exceed threshold on container apps console logs'
    severity: 2
    enabled: true
    scopes: [
      workspaceId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      allOf: [
        {
          query: 'ContainerAppConsoleLogs_CL | where Log_s has " 500 " or Log_s has " 502 " or Log_s has " 503 " or Log_s has "InternalServerError"'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 10
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
  }
}

// ── 4. High Severity: Container App CrashLoop / Restart Spikes (Sev-2) ──────────
resource alertContainerRestarts 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-container-restarts-${environment}'
  location: location
  tags: tags
  properties: {
    displayName: 'High: Container App Restart / CrashLoop (${environment})'
    description: 'Fires when Container Apps system logs report crash loop or failed container instances'
    severity: 2
    enabled: true
    scopes: [
      workspaceId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      allOf: [
        {
          query: 'ContainerAppSystemLogs_CL | where Reason_s in~ ("CrashLoopBackOff", "OOMKilled", "ContainerFailed") or Log_s has "restarting"'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 2
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
  }
}

// ── 5. High Severity: PostgreSQL Active Connection Saturation (Sev-2) ──────────
resource alertPostgresConnections 'Microsoft.Insights/metricAlerts@2018-03-01' = if (!empty(postgresServerId)) {
  name: 'alert-postgres-connections-${environment}'
  location: 'global'
  tags: tags
  properties: {
    description: 'Fires when PostgreSQL active connections exceed 80 connections'
    severity: 2
    enabled: true
    scopes: [
      postgresServerId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          name: 'PostgresActiveConnections'
          metricName: 'active_connections'
          operator: 'GreaterThan'
          threshold: 80
          timeAggregation: 'Average'
          criterionType: 'StaticThresholdCriterion'
        }
      ]
    }
    actions: [
      {
        actionGroupId: actionGroup.id
      }
    ]
  }
}

// ── 6. High Severity: Key Vault Unauthorized 403 Bursts (Sev-2) ─────────────────
resource alertKeyVaultUnauthorized 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-keyvault-unauthorized-${environment}'
  location: location
  tags: tags
  properties: {
    displayName: 'High: Key Vault 403 Forbidden Bursts (${environment})'
    description: 'Fires when unauthorized access attempts to Key Vault occur repeatedly'
    severity: 2
    enabled: true
    scopes: [
      workspaceId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      allOf: [
        {
          query: 'AzureDiagnostics | where ResourceProvider == "MICROSOFT.KEYVAULT" and httpStatusCode_d == 403'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 5
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
  }
}

output actionGroupId string = actionGroup.id
output actionGroupName string = actionGroup.name
