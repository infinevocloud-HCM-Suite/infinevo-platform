@description('Display name of the Azure Monitor Workbook')
param workbookDisplayName string = 'Infinevo Platform Telemetry'

@description('Azure region for the workbook')
param location string = resourceGroup().location

@description('Resource ID of the Log Analytics workspace')
param workspaceId string

@description('Tags for the resource')
param tags object = {}

resource workbook 'Microsoft.Insights/workbooks@2022-04-01' = {
  name: guid(resourceGroup().id, 'infinevo-telemetry-workbook')
  location: location
  tags: tags
  kind: 'shared'
  properties: {
    displayName: workbookDisplayName
    category: 'workbook'
    sourceId: workspaceId
    serializedData: string({
      version: 'Notebook/1.0'
      items: [
        {
          type: 1
          content: {
            json: '## Infinevo HCM Platform Telemetry\nReal-time monitoring across Web API (`app`) and Worker batch execution (`PLAT-07`).'
          }
        }
        {
          type: 3
          content: {
            version: 'KqlItem/1.0'
            query: 'ContainerAppConsoleLogs_CL | where TimeGenerated > ago(1h) | summarize TotalLogs=count() by bin(TimeGenerated, 5m)'
            size: 0
            title: 'Total Console Logs (5m bins)'
            timeContext: {
              durationMs: 3600000
            }
            queryType: 0
            resourceType: 'microsoft.operationalinsights/workspaces'
          }
        }
        {
          type: 3
          content: {
            version: 'KqlItem/1.0'
            query: 'ContainerAppConsoleLogs_CL | where TimeGenerated > ago(1h) and (Log_s has "ERROR" or Log_s has "Exception" or Log_s has "500") | summarize ErrorCount=count() by bin(TimeGenerated, 5m)'
            size: 0
            title: 'Application Errors & 5xx Spikes (5m bins)'
            timeContext: {
              durationMs: 3600000
            }
            queryType: 0
            resourceType: 'microsoft.operationalinsights/workspaces'
          }
        }
        {
          type: 3
          content: {
            version: 'KqlItem/1.0'
            query: 'AppRequests | where TimeGenerated > ago(1h) | summarize AvgDurationMs=avg(DurationMs), P95DurationMs=percentile(DurationMs, 95) by bin(TimeGenerated, 5m)'
            size: 0
            title: 'HTTP Request Latency (Avg & P95)'
            timeContext: {
              durationMs: 3600000
            }
            queryType: 0
            resourceType: 'microsoft.operationalinsights/workspaces'
          }
        }
        {
          type: 3
          content: {
            version: 'KqlItem/1.0'
            query: 'AzureMetrics | where TimeGenerated > ago(1h) and MetricName == "UsageNanoCores" | summarize AvgCpuCores = avg(Average) / 1000000000 by bin(TimeGenerated, 5m), Resource'
            size: 0
            title: 'Container CPU Usage (Cores)'
            timeContext: {
              durationMs: 3600000
            }
            queryType: 0
            resourceType: 'microsoft.operationalinsights/workspaces'
          }
        }
        {
          type: 3
          content: {
            version: 'KqlItem/1.0'
            query: 'AzureMetrics | where TimeGenerated > ago(1h) and MetricName == "WorkingSetBytes" | summarize AvgMemoryMB = avg(Average) / (1024 * 1024) by bin(TimeGenerated, 5m), Resource'
            size: 0
            title: 'Container Memory Usage (MB)'
            timeContext: {
              durationMs: 3600000
            }
            queryType: 0
            resourceType: 'microsoft.operationalinsights/workspaces'
          }
        }
      ]
    })
  }
}

output workbookId string = workbook.id
output workbookName string = workbook.name
