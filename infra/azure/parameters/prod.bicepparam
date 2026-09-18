using '../main.bicep'

param environment = 'prod'
param location = 'centralindia'

param postgresAdminUsername = 'infinevo_admin'
param postgresAdminPassword = 'Prod_Postgres_Password_2026!'

param postgresSkuName = 'Standard_D2ds_v5'
param postgresSkuTier = 'GeneralPurpose'
param postgresStorageSizeGB = 128
param postgresHighAvailability = 'ZoneRedundant'

param redisSkuFamily = 'C'
param redisSkuName = 'Standard'
param redisSkuCapacity = 1

param storageSkuName = 'Standard_ZRS'
param acrSku = 'Standard'

param containerAppCpu = '0.5'
param containerAppMemory = '1.0Gi'
param containerAppMinReplicas = 2
param containerAppMaxReplicas = 10

param enablePurgeProtection = true
