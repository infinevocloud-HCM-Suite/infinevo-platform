using '../main.bicep'

param environment = 'prod'
param location = 'centralindia'

param postgresAdminUsername = 'infinevo_admin'

param postgresSkuName = 'Standard_D2ds_v5'
param postgresSkuTier = 'GeneralPurpose'
param postgresStorageSizeGB = 128
param postgresHighAvailability = 'ZoneRedundant'

param redisSkuFamily = 'C'
param redisSkuName = 'Standard'
param redisSkuCapacity = 1

param storageSkuName = 'Standard_ZRS'

param containerAppCpu = '0.5'
param containerAppMemory = '1.0Gi'
param containerAppMinReplicas = 2
param containerAppMaxReplicas = 10

// Networking (W-51 section 2.1). 10.30.0.0/16; 10.30.2.0/24 is left unallocated.
param vnetAddressPrefix = '10.30.0.0/16'
param caeSubnetPrefix = '10.30.0.0/23'
param peSubnetPrefix = '10.30.3.0/24'

param enablePurgeProtection = true
