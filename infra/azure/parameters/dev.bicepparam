using '../main.bicep'

param environment = 'dev'
param location = 'centralindia'

param postgresAdminUsername = 'infinevo_admin'

param postgresSkuName = 'Standard_B1ms'
param postgresSkuTier = 'Burstable'
param postgresStorageSizeGB = 32
param postgresHighAvailability = 'Disabled'

param redisSkuFamily = 'C'
param redisSkuName = 'Basic'
param redisSkuCapacity = 0

param storageSkuName = 'Standard_LRS'

param containerAppCpu = '0.25'
param containerAppMemory = '0.5Gi'
param containerAppMinReplicas = 0
param containerAppMaxReplicas = 3

// Networking (W-51 section 2.1). 10.10.0.0/16; 10.10.2.0/24 is left unallocated.
param vnetAddressPrefix = '10.10.0.0/16'
param caeSubnetPrefix = '10.10.0.0/23'
param peSubnetPrefix = '10.10.3.0/24'

param enablePurgeProtection = false
