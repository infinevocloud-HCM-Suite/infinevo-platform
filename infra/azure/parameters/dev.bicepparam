using '../main.bicep'

param environment = 'dev'
param location = 'centralindia'

param postgresAdminUsername = 'infinevo_admin'
param postgresAdminPassword = 'Dev_Postgres_Password_2026!'

param postgresSkuName = 'Standard_B1ms'
param postgresSkuTier = 'Burstable'
param postgresStorageSizeGB = 32
param postgresHighAvailability = 'Disabled'

param redisSkuFamily = 'C'
param redisSkuName = 'Basic'
param redisSkuCapacity = 0

param storageSkuName = 'Standard_LRS'
param acrSku = 'Basic'

param containerAppCpu = '0.25'
param containerAppMemory = '0.5Gi'
param containerAppMinReplicas = 0
param containerAppMaxReplicas = 3

param enablePurgeProtection = false
