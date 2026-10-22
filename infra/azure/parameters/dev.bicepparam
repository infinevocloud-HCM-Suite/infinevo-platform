using '../main.bicep'

param environment = 'dev'
param location = 'centralindia'

param postgresAdminUsername = 'infinevo_admin'

param postgresSkuName = 'Standard_B1ms'
param postgresSkuTier = 'Burstable'
param postgresStorageSizeGB = 32
param postgresHighAvailability = 'Disabled'
param postgresBackupRetentionDays = 7

param redisSkuName = 'Balanced_B0'
param deployRedis = true

param storageSkuName = 'Standard_LRS'

param containerAppCpu = '0.5'
param containerAppMemory = '1.0Gi'
param containerAppMinReplicas = 1
param containerAppMaxReplicas = 3
param workerMinReplicas = 0

// Networking (W-51 section 2.1). 10.10.0.0/16; 10.10.2.0/24 is left unallocated.
param vnetAddressPrefix = '10.10.0.0/16'
param caeSubnetPrefix = '10.10.0.0/23'
param peSubnetPrefix = '10.10.3.0/24'

param enablePurgeProtection = false
param alertEmail = 'kmohapatra@infinevocloud.com'
// Founder step (W-10.1): Brevo SMTP login (Brevo > SMTP & API > SMTP).
param brevoSmtpLogin = 'bc91dc001@smtp-brevo.com'
param brevoSenderEmail = 'application@infinevocloud.com'

// W-71: caj-data-migration-dev runs crinfinevo.azurecr.io/data-migration at this tag.
// Built by hand (HANDBOOK 4.6), never by the release pipeline. A fixed tag, never
// 'latest' (W-71 Task 3, W-54 F-16): a rehearsal must name the image it ran.
// Set to '' to leave the job out of the deployment.
param dataMigrationImageTag = '2026-10-22.1'
