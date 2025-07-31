# Azure Infrastructure as Code (ARM Templates)

This document contains Azure Resource Manager (ARM) templates to provision the infrastructure required for the Asset Manager application migration to Azure.

## Main Template (azuredeploy.json)

```json
{
    "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
    "contentVersion": "1.0.0.0",
    "parameters": {
        "projectName": {
            "type": "string",
            "defaultValue": "assets-manager",
            "metadata": {
                "description": "Name of the project used for resource naming"
            }
        },
        "environment": {
            "type": "string",
            "defaultValue": "prod",
            "allowedValues": [
                "dev",
                "staging",
                "prod"
            ],
            "metadata": {
                "description": "Environment name"
            }
        },
        "location": {
            "type": "string",
            "defaultValue": "[resourceGroup().location]",
            "metadata": {
                "description": "Location for all resources"
            }
        },
        "administratorLogin": {
            "type": "string",
            "metadata": {
                "description": "Administrator login for PostgreSQL server"
            }
        },
        "administratorLoginPassword": {
            "type": "securestring",
            "metadata": {
                "description": "Administrator password for PostgreSQL server"
            }
        },
        "appServicePlanSku": {
            "type": "object",
            "defaultValue": {
                "name": "P1V3",
                "tier": "Premium",
                "capacity": 2
            },
            "metadata": {
                "description": "App Service Plan SKU configuration"
            }
        }
    },
    "variables": {
        "resourceNamePrefix": "[concat(parameters('projectName'), '-', parameters('environment'))]",
        "storageAccountName": "[concat(replace(parameters('projectName'), '-', ''), parameters('environment'), 'storage')]",
        "appServicePlanName": "[concat(variables('resourceNamePrefix'), '-plan')]",
        "webAppName": "[concat(variables('resourceNamePrefix'), '-web')]",
        "workerAppName": "[concat(variables('resourceNamePrefix'), '-worker')]",
        "postgreSQLServerName": "[concat(variables('resourceNamePrefix'), '-db')]",
        "serviceBusNamespaceName": "[concat(variables('resourceNamePrefix'), '-servicebus')]",
        "keyVaultName": "[concat(variables('resourceNamePrefix'), '-keyvault')]",
        "applicationInsightsName": "[concat(variables('resourceNamePrefix'), '-insights')]",
        "logAnalyticsWorkspaceName": "[concat(variables('resourceNamePrefix'), '-logs')]"
    },
    "resources": [
        {
            "type": "Microsoft.Storage/storageAccounts",
            "apiVersion": "2021-09-01",
            "name": "[variables('storageAccountName')]",
            "location": "[parameters('location')]",
            "sku": {
                "name": "Standard_LRS"
            },
            "kind": "StorageV2",
            "properties": {
                "accessTier": "Hot",
                "allowBlobPublicAccess": false,
                "supportsHttpsTrafficOnly": true,
                "minimumTlsVersion": "TLS1_2"
            },
            "resources": [
                {
                    "type": "blobServices/containers",
                    "apiVersion": "2021-09-01",
                    "name": "default/assets-container",
                    "dependsOn": [
                        "[resourceId('Microsoft.Storage/storageAccounts', variables('storageAccountName'))]"
                    ],
                    "properties": {
                        "publicAccess": "None"
                    }
                }
            ]
        },
        {
            "type": "Microsoft.DBforPostgreSQL/servers",
            "apiVersion": "2017-12-01",
            "name": "[variables('postgreSQLServerName')]",
            "location": "[parameters('location')]",
            "sku": {
                "name": "GP_Gen5_2",
                "tier": "GeneralPurpose",
                "family": "Gen5",
                "capacity": 2
            },
            "properties": {
                "administratorLogin": "[parameters('administratorLogin')]",
                "administratorLoginPassword": "[parameters('administratorLoginPassword')]",
                "version": "11",
                "sslEnforcement": "Enabled",
                "minimalTlsVersion": "TLS1_2",
                "storageProfile": {
                    "storageMB": 102400,
                    "backupRetentionDays": 7,
                    "geoRedundantBackup": "Disabled",
                    "storageAutogrow": "Enabled"
                }
            },
            "resources": [
                {
                    "type": "databases",
                    "apiVersion": "2017-12-01",
                    "name": "assets_manager",
                    "dependsOn": [
                        "[resourceId('Microsoft.DBforPostgreSQL/servers', variables('postgreSQLServerName'))]"
                    ],
                    "properties": {
                        "charset": "UTF8",
                        "collation": "en_US.utf8"
                    }
                },
                {
                    "type": "firewallRules",
                    "apiVersion": "2017-12-01",
                    "name": "AllowAzureServices",
                    "dependsOn": [
                        "[resourceId('Microsoft.DBforPostgreSQL/servers', variables('postgreSQLServerName'))]"
                    ],
                    "properties": {
                        "startIpAddress": "0.0.0.0",
                        "endIpAddress": "0.0.0.0"
                    }
                }
            ]
        },
        {
            "type": "Microsoft.ServiceBus/namespaces",
            "apiVersion": "2021-11-01",
            "name": "[variables('serviceBusNamespaceName')]",
            "location": "[parameters('location')]",
            "sku": {
                "name": "Standard",
                "tier": "Standard"
            },
            "properties": {
                "disableLocalAuth": false
            },
            "resources": [
                {
                    "type": "topics",
                    "apiVersion": "2021-11-01",
                    "name": "image-processing-topic",
                    "dependsOn": [
                        "[resourceId('Microsoft.ServiceBus/namespaces', variables('serviceBusNamespaceName'))]"
                    ],
                    "properties": {
                        "maxSizeInMegabytes": 1024,
                        "defaultMessageTimeToLive": "P14D",
                        "enablePartitioning": false
                    },
                    "resources": [
                        {
                            "type": "subscriptions",
                            "apiVersion": "2021-11-01",
                            "name": "worker-subscription",
                            "dependsOn": [
                                "[resourceId('Microsoft.ServiceBus/namespaces/topics', variables('serviceBusNamespaceName'), 'image-processing-topic')]"
                            ],
                            "properties": {
                                "maxDeliveryCount": 10,
                                "defaultMessageTimeToLive": "P14D",
                                "lockDuration": "PT30S",
                                "enableDeadLetteringOnMessageExpiration": true
                            }
                        }
                    ]
                }
            ]
        },
        {
            "type": "Microsoft.OperationalInsights/workspaces",
            "apiVersion": "2021-12-01-preview",
            "name": "[variables('logAnalyticsWorkspaceName')]",
            "location": "[parameters('location')]",
            "properties": {
                "sku": {
                    "name": "PerGB2018"
                },
                "retentionInDays": 30
            }
        },
        {
            "type": "Microsoft.Insights/components",
            "apiVersion": "2020-02-02",
            "name": "[variables('applicationInsightsName')]",
            "location": "[parameters('location')]",
            "kind": "web",
            "dependsOn": [
                "[resourceId('Microsoft.OperationalInsights/workspaces', variables('logAnalyticsWorkspaceName'))]"
            ],
            "properties": {
                "Application_Type": "web",
                "WorkspaceResourceId": "[resourceId('Microsoft.OperationalInsights/workspaces', variables('logAnalyticsWorkspaceName'))]"
            }
        },
        {
            "type": "Microsoft.KeyVault/vaults",
            "apiVersion": "2021-11-01-preview",
            "name": "[variables('keyVaultName')]",
            "location": "[parameters('location')]",
            "properties": {
                "sku": {
                    "family": "A",
                    "name": "standard"
                },
                "tenantId": "[subscription().tenantId]",
                "enabledForDeployment": false,
                "enabledForDiskEncryption": false,
                "enabledForTemplateDeployment": true,
                "enableSoftDelete": true,
                "softDeleteRetentionInDays": 7,
                "accessPolicies": []
            }
        },
        {
            "type": "Microsoft.Web/serverfarms",
            "apiVersion": "2021-03-01",
            "name": "[variables('appServicePlanName')]",
            "location": "[parameters('location')]",
            "sku": "[parameters('appServicePlanSku')]",
            "kind": "app",
            "properties": {
                "reserved": false
            }
        },
        {
            "type": "Microsoft.Web/sites",
            "apiVersion": "2021-03-01",
            "name": "[variables('webAppName')]",
            "location": "[parameters('location')]",
            "dependsOn": [
                "[resourceId('Microsoft.Web/serverfarms', variables('appServicePlanName'))]",
                "[resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))]"
            ],
            "identity": {
                "type": "SystemAssigned"
            },
            "properties": {
                "serverFarmId": "[resourceId('Microsoft.Web/serverfarms', variables('appServicePlanName'))]",
                "httpsOnly": true,
                "siteConfig": {
                    "minTlsVersion": "1.2",
                    "javaVersion": "11",
                    "javaContainer": "JAVA",
                    "javaContainerVersion": "11",
                    "appSettings": [
                        {
                            "name": "AZURE_STORAGE_ACCOUNT_NAME",
                            "value": "[variables('storageAccountName')]"
                        },
                        {
                            "name": "AZURE_DB_SERVER",
                            "value": "[concat(variables('postgreSQLServerName'), '.postgres.database.azure.com')]"
                        },
                        {
                            "name": "AZURE_DB_USERNAME",
                            "value": "[concat(parameters('administratorLogin'), '@', variables('postgreSQLServerName'))]"
                        },
                        {
                            "name": "SERVICE_BUS_NAMESPACE",
                            "value": "[concat(variables('serviceBusNamespaceName'), '.servicebus.windows.net')]"
                        },
                        {
                            "name": "APPINSIGHTS_INSTRUMENTATION_KEY",
                            "value": "[reference(resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))).InstrumentationKey]"
                        },
                        {
                            "name": "APPLICATIONINSIGHTS_CONNECTION_STRING",
                            "value": "[reference(resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))).ConnectionString]"
                        }
                    ]
                }
            }
        },
        {
            "type": "Microsoft.Web/sites",
            "apiVersion": "2021-03-01",
            "name": "[variables('workerAppName')]",
            "location": "[parameters('location')]",
            "dependsOn": [
                "[resourceId('Microsoft.Web/serverfarms', variables('appServicePlanName'))]",
                "[resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))]"
            ],
            "identity": {
                "type": "SystemAssigned"
            },
            "properties": {
                "serverFarmId": "[resourceId('Microsoft.Web/serverfarms', variables('appServicePlanName'))]",
                "httpsOnly": true,
                "siteConfig": {
                    "minTlsVersion": "1.2",
                    "javaVersion": "11",
                    "javaContainer": "JAVA",
                    "javaContainerVersion": "11",
                    "appSettings": [
                        {
                            "name": "AZURE_STORAGE_ACCOUNT_NAME",
                            "value": "[variables('storageAccountName')]"
                        },
                        {
                            "name": "AZURE_DB_SERVER",
                            "value": "[concat(variables('postgreSQLServerName'), '.postgres.database.azure.com')]"
                        },
                        {
                            "name": "AZURE_DB_USERNAME",
                            "value": "[concat(parameters('administratorLogin'), '@', variables('postgreSQLServerName'))]"
                        },
                        {
                            "name": "SERVICE_BUS_NAMESPACE",
                            "value": "[concat(variables('serviceBusNamespaceName'), '.servicebus.windows.net')]"
                        },
                        {
                            "name": "APPINSIGHTS_INSTRUMENTATION_KEY",
                            "value": "[reference(resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))).InstrumentationKey]"
                        },
                        {
                            "name": "APPLICATIONINSIGHTS_CONNECTION_STRING",
                            "value": "[reference(resourceId('Microsoft.Insights/components', variables('applicationInsightsName'))).ConnectionString]"
                        }
                    ]
                }
            }
        }
    ],
    "outputs": {
        "storageAccountName": {
            "type": "string",
            "value": "[variables('storageAccountName')]"
        },
        "webAppName": {
            "type": "string",
            "value": "[variables('webAppName')]"
        },
        "workerAppName": {
            "type": "string",
            "value": "[variables('workerAppName')]"
        },
        "databaseServerName": {
            "type": "string",
            "value": "[variables('postgreSQLServerName')]"
        },
        "serviceBusNamespace": {
            "type": "string",
            "value": "[variables('serviceBusNamespaceName')]"
        },
        "keyVaultName": {
            "type": "string",
            "value": "[variables('keyVaultName')]"
        },
        "applicationInsightsName": {
            "type": "string",
            "value": "[variables('applicationInsightsName')]"
        },
        "webAppManagedIdentityPrincipalId": {
            "type": "string",
            "value": "[reference(resourceId('Microsoft.Web/sites', variables('webAppName')), '2021-03-01', 'Full').identity.principalId]"
        },
        "workerAppManagedIdentityPrincipalId": {
            "type": "string",
            "value": "[reference(resourceId('Microsoft.Web/sites', variables('workerAppName')), '2021-03-01', 'Full').identity.principalId]"
        }
    }
}
```

## RBAC Template (rbac.json)

```json
{
    "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
    "contentVersion": "1.0.0.0",
    "parameters": {
        "webAppPrincipalId": {
            "type": "string",
            "metadata": {
                "description": "Principal ID of the web app managed identity"
            }
        },
        "workerAppPrincipalId": {
            "type": "string",
            "metadata": {
                "description": "Principal ID of the worker app managed identity"
            }
        },
        "storageAccountName": {
            "type": "string",
            "metadata": {
                "description": "Name of the storage account"
            }
        },
        "serviceBusNamespaceName": {
            "type": "string",
            "metadata": {
                "description": "Name of the Service Bus namespace"
            }
        },
        "keyVaultName": {
            "type": "string",
            "metadata": {
                "description": "Name of the Key Vault"
            }
        }
    },
    "variables": {
        "storageBlobDataContributorRoleId": "[subscriptionResourceId('Microsoft.Authorization/roleDefinitions', 'ba92f5b4-2d11-453d-a403-e96b0029c9fe')]",
        "serviceBusDataOwnerRoleId": "[subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '090c5cfd-751d-490a-894a-3ce6f1109419')]",
        "keyVaultSecretsUserRoleId": "[subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')]"
    },
    "resources": [
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('webAppPrincipalId'), 'StorageBlobDataContributor')]",
            "scope": "[resourceId('Microsoft.Storage/storageAccounts', parameters('storageAccountName'))]",
            "properties": {
                "roleDefinitionId": "[variables('storageBlobDataContributorRoleId')]",
                "principalId": "[parameters('webAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        },
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('workerAppPrincipalId'), 'StorageBlobDataContributor')]",
            "scope": "[resourceId('Microsoft.Storage/storageAccounts', parameters('storageAccountName'))]",
            "properties": {
                "roleDefinitionId": "[variables('storageBlobDataContributorRoleId')]",
                "principalId": "[parameters('workerAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        },
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('webAppPrincipalId'), 'ServiceBusDataOwner')]",
            "scope": "[resourceId('Microsoft.ServiceBus/namespaces', parameters('serviceBusNamespaceName'))]",
            "properties": {
                "roleDefinitionId": "[variables('serviceBusDataOwnerRoleId')]",
                "principalId": "[parameters('webAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        },
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('workerAppPrincipalId'), 'ServiceBusDataOwner')]",
            "scope": "[resourceId('Microsoft.ServiceBus/namespaces', parameters('serviceBusNamespaceName'))]",
            "properties": {
                "roleDefinitionId": "[variables('serviceBusDataOwnerRoleId')]",
                "principalId": "[parameters('workerAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        },
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('webAppPrincipalId'), 'KeyVaultSecretsUser')]",
            "scope": "[resourceId('Microsoft.KeyVault/vaults', parameters('keyVaultName'))]",
            "properties": {
                "roleDefinitionId": "[variables('keyVaultSecretsUserRoleId')]",
                "principalId": "[parameters('webAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        },
        {
            "type": "Microsoft.Authorization/roleAssignments",
            "apiVersion": "2020-04-01-preview",
            "name": "[guid(resourceGroup().id, parameters('workerAppPrincipalId'), 'KeyVaultSecretsUser')]",
            "scope": "[resourceId('Microsoft.KeyVault/vaults', parameters('keyVaultName'))]",
            "properties": {
                "roleDefinitionId": "[variables('keyVaultSecretsUserRoleId')]",
                "principalId": "[parameters('workerAppPrincipalId')]",
                "principalType": "ServicePrincipal"
            }
        }
    ]
}
```

## Parameters File (azuredeploy.parameters.json)

```json
{
    "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#",
    "contentVersion": "1.0.0.0",
    "parameters": {
        "projectName": {
            "value": "assets-manager"
        },
        "environment": {
            "value": "prod"
        },
        "location": {
            "value": "East US"
        },
        "administratorLogin": {
            "value": "adminuser"
        },
        "administratorLoginPassword": {
            "reference": {
                "keyVault": {
                    "id": "/subscriptions/{subscription-id}/resourceGroups/{rg-name}/providers/Microsoft.KeyVault/vaults/{keyvault-name}"
                },
                "secretName": "postgresql-admin-password"
            }
        },
        "appServicePlanSku": {
            "value": {
                "name": "P1V3",
                "tier": "Premium",
                "capacity": 2
            }
        }
    }
}
```

## Deployment Scripts

### PowerShell Deployment Script (deploy.ps1)

```powershell
#!/usr/bin/env pwsh

param(
    [Parameter(Mandatory=$true)]
    [string]$SubscriptionId,
    
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName,
    
    [Parameter(Mandatory=$true)]
    [string]$Location,
    
    [Parameter(Mandatory=$false)]
    [string]$Environment = "prod",
    
    [Parameter(Mandatory=$false)]
    [string]$TemplateFile = "azuredeploy.json",
    
    [Parameter(Mandatory=$false)]
    [string]$ParametersFile = "azuredeploy.parameters.json"
)

# Set error action preference
$ErrorActionPreference = "Stop"

Write-Host "Starting Azure deployment..." -ForegroundColor Green

# Login to Azure (if not already logged in)
$context = Get-AzContext
if (!$context) {
    Write-Host "Logging into Azure..." -ForegroundColor Yellow
    Connect-AzAccount
}

# Set subscription context
Write-Host "Setting subscription context to: $SubscriptionId" -ForegroundColor Yellow
Set-AzContext -SubscriptionId $SubscriptionId

# Create resource group if it doesn't exist
$rg = Get-AzResourceGroup -Name $ResourceGroupName -ErrorAction SilentlyContinue
if (!$rg) {
    Write-Host "Creating resource group: $ResourceGroupName" -ForegroundColor Yellow
    New-AzResourceGroup -Name $ResourceGroupName -Location $Location
}

# Deploy main template
Write-Host "Deploying main infrastructure template..." -ForegroundColor Yellow
$mainDeployment = New-AzResourceGroupDeployment `
    -ResourceGroupName $ResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $ParametersFile `
    -Verbose

if ($mainDeployment.ProvisioningState -eq "Succeeded") {
    Write-Host "Main deployment completed successfully!" -ForegroundColor Green
    
    # Extract outputs
    $outputs = $mainDeployment.Outputs
    $webAppPrincipalId = $outputs.webAppManagedIdentityPrincipalId.Value
    $workerAppPrincipalId = $outputs.workerAppManagedIdentityPrincipalId.Value
    $storageAccountName = $outputs.storageAccountName.Value
    $serviceBusNamespace = $outputs.serviceBusNamespace.Value
    $keyVaultName = $outputs.keyVaultName.Value
    
    # Deploy RBAC template
    Write-Host "Deploying RBAC configuration..." -ForegroundColor Yellow
    $rbacDeployment = New-AzResourceGroupDeployment `
        -ResourceGroupName $ResourceGroupName `
        -TemplateFile "rbac.json" `
        -webAppPrincipalId $webAppPrincipalId `
        -workerAppPrincipalId $workerAppPrincipalId `
        -storageAccountName $storageAccountName `
        -serviceBusNamespaceName $serviceBusNamespace `
        -keyVaultName $keyVaultName `
        -Verbose
    
    if ($rbacDeployment.ProvisioningState -eq "Succeeded") {
        Write-Host "RBAC deployment completed successfully!" -ForegroundColor Green
        
        # Output deployment information
        Write-Host "`nDeployment Summary:" -ForegroundColor Cyan
        Write-Host "===================" -ForegroundColor Cyan
        Write-Host "Resource Group: $ResourceGroupName" -ForegroundColor White
        Write-Host "Web App: $($outputs.webAppName.Value)" -ForegroundColor White
        Write-Host "Worker App: $($outputs.workerAppName.Value)" -ForegroundColor White
        Write-Host "Storage Account: $($outputs.storageAccountName.Value)" -ForegroundColor White
        Write-Host "Database Server: $($outputs.databaseServerName.Value)" -ForegroundColor White
        Write-Host "Service Bus: $($outputs.serviceBusNamespace.Value)" -ForegroundColor White
        Write-Host "Key Vault: $($outputs.keyVaultName.Value)" -ForegroundColor White
        Write-Host "Application Insights: $($outputs.applicationInsightsName.Value)" -ForegroundColor White
        
    } else {
        Write-Error "RBAC deployment failed!"
        exit 1
    }
} else {
    Write-Error "Main deployment failed!"
    exit 1
}

Write-Host "`nDeployment completed successfully!" -ForegroundColor Green
```

### Azure CLI Deployment Script (deploy.sh)

```bash
#!/bin/bash

# Set script to exit on any error
set -e

# Default values
ENVIRONMENT="prod"
TEMPLATE_FILE="azuredeploy.json"
PARAMETERS_FILE="azuredeploy.parameters.json"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--subscription)
            SUBSCRIPTION_ID="$2"
            shift 2
            ;;
        -g|--resource-group)
            RESOURCE_GROUP_NAME="$2"
            shift 2
            ;;
        -l|--location)
            LOCATION="$2"
            shift 2
            ;;
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -t|--template)
            TEMPLATE_FILE="$2"
            shift 2
            ;;
        -p|--parameters)
            PARAMETERS_FILE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option $1"
            exit 1
            ;;
    esac
done

# Validate required parameters
if [[ -z "$SUBSCRIPTION_ID" || -z "$RESOURCE_GROUP_NAME" || -z "$LOCATION" ]]; then
    echo "Usage: $0 -s SUBSCRIPTION_ID -g RESOURCE_GROUP_NAME -l LOCATION [-e ENVIRONMENT] [-t TEMPLATE_FILE] [-p PARAMETERS_FILE]"
    exit 1
fi

echo "Starting Azure deployment..."

# Login to Azure (if not already logged in)
if ! az account show &> /dev/null; then
    echo "Logging into Azure..."
    az login
fi

# Set subscription context
echo "Setting subscription context to: $SUBSCRIPTION_ID"
az account set --subscription "$SUBSCRIPTION_ID"

# Create resource group if it doesn't exist
echo "Creating resource group: $RESOURCE_GROUP_NAME"
az group create --name "$RESOURCE_GROUP_NAME" --location "$LOCATION"

# Deploy main template
echo "Deploying main infrastructure template..."
MAIN_DEPLOYMENT=$(az deployment group create \
    --resource-group "$RESOURCE_GROUP_NAME" \
    --template-file "$TEMPLATE_FILE" \
    --parameters "@$PARAMETERS_FILE" \
    --query "properties.outputs" \
    --output json)

if [[ $? -eq 0 ]]; then
    echo "Main deployment completed successfully!"
    
    # Extract outputs
    WEB_APP_PRINCIPAL_ID=$(echo "$MAIN_DEPLOYMENT" | jq -r '.webAppManagedIdentityPrincipalId.value')
    WORKER_APP_PRINCIPAL_ID=$(echo "$MAIN_DEPLOYMENT" | jq -r '.workerAppManagedIdentityPrincipalId.value')
    STORAGE_ACCOUNT_NAME=$(echo "$MAIN_DEPLOYMENT" | jq -r '.storageAccountName.value')
    SERVICE_BUS_NAMESPACE=$(echo "$MAIN_DEPLOYMENT" | jq -r '.serviceBusNamespace.value')
    KEY_VAULT_NAME=$(echo "$MAIN_DEPLOYMENT" | jq -r '.keyVaultName.value')
    
    # Deploy RBAC template
    echo "Deploying RBAC configuration..."
    az deployment group create \
        --resource-group "$RESOURCE_GROUP_NAME" \
        --template-file "rbac.json" \
        --parameters \
            webAppPrincipalId="$WEB_APP_PRINCIPAL_ID" \
            workerAppPrincipalId="$WORKER_APP_PRINCIPAL_ID" \
            storageAccountName="$STORAGE_ACCOUNT_NAME" \
            serviceBusNamespaceName="$SERVICE_BUS_NAMESPACE" \
            keyVaultName="$KEY_VAULT_NAME"
    
    if [[ $? -eq 0 ]]; then
        echo "RBAC deployment completed successfully!"
        
        # Output deployment information
        echo ""
        echo "Deployment Summary:"
        echo "==================="
        echo "Resource Group: $RESOURCE_GROUP_NAME"
        echo "Web App: $(echo "$MAIN_DEPLOYMENT" | jq -r '.webAppName.value')"
        echo "Worker App: $(echo "$MAIN_DEPLOYMENT" | jq -r '.workerAppName.value')"
        echo "Storage Account: $(echo "$MAIN_DEPLOYMENT" | jq -r '.storageAccountName.value')"
        echo "Database Server: $(echo "$MAIN_DEPLOYMENT" | jq -r '.databaseServerName.value')"
        echo "Service Bus: $(echo "$MAIN_DEPLOYMENT" | jq -r '.serviceBusNamespace.value')"
        echo "Key Vault: $(echo "$MAIN_DEPLOYMENT" | jq -r '.keyVaultName.value')"
        echo "Application Insights: $(echo "$MAIN_DEPLOYMENT" | jq -r '.applicationInsightsName.value')"
        
    else
        echo "RBAC deployment failed!"
        exit 1
    fi
else
    echo "Main deployment failed!"
    exit 1
fi

echo ""
echo "Deployment completed successfully!"
```

## Post-Deployment Configuration Script

### Database Initialization Script (init-database.sql)

```sql
-- Create database user for application
CREATE USER app_user WITH PASSWORD 'your-app-password';

-- Grant necessary permissions
GRANT CONNECT ON DATABASE assets_manager TO app_user;
GRANT USAGE ON SCHEMA public TO app_user;
GRANT CREATE ON SCHEMA public TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_user;

-- Create application tables (if not using Hibernate auto-generation)
CREATE TABLE IF NOT EXISTS image_metadata (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_size BIGINT,
    content_type VARCHAR(100),
    blob_name VARCHAR(255) UNIQUE,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    processing_date TIMESTAMP,
    created_by VARCHAR(100),
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_image_metadata_blob_name ON image_metadata(blob_name);
CREATE INDEX IF NOT EXISTS idx_image_metadata_upload_date ON image_metadata(upload_date);
CREATE INDEX IF NOT EXISTS idx_image_metadata_processed ON image_metadata(processed);
```

### Key Vault Secrets Setup Script (setup-keyvault.ps1)

```powershell
param(
    [Parameter(Mandatory=$true)]
    [string]$KeyVaultName,
    
    [Parameter(Mandatory=$true)]
    [string]$DatabasePassword,
    
    [Parameter(Mandatory=$true)]
    [string]$StorageConnectionString,
    
    [Parameter(Mandatory=$true)]
    [string]$ServiceBusConnectionString
)

Write-Host "Setting up Key Vault secrets..." -ForegroundColor Green

# Set database password
Write-Host "Setting database password secret..." -ForegroundColor Yellow
$dbPasswordSecret = ConvertTo-SecureString -String $DatabasePassword -AsPlainText -Force
Set-AzKeyVaultSecret -VaultName $KeyVaultName -Name "database-password" -SecretValue $dbPasswordSecret

# Set storage connection string
Write-Host "Setting storage connection string secret..." -ForegroundColor Yellow
$storageSecret = ConvertTo-SecureString -String $StorageConnectionString -AsPlainText -Force
Set-AzKeyVaultSecret -VaultName $KeyVaultName -Name "storage-connection-string" -SecretValue $storageSecret

# Set service bus connection string
Write-Host "Setting service bus connection string secret..." -ForegroundColor Yellow
$serviceBusSecret = ConvertTo-SecureString -String $ServiceBusConnectionString -AsPlainText -Force
Set-AzKeyVaultSecret -VaultName $KeyVaultName -Name "servicebus-connection-string" -SecretValue $serviceBusSecret

Write-Host "Key Vault secrets setup completed!" -ForegroundColor Green
```

## Deployment Instructions

### Prerequisites

1. **Azure CLI or PowerShell**: Install Azure CLI or Azure PowerShell module
2. **Permissions**: Ensure you have Contributor role on the target subscription
3. **Resource Naming**: Verify that resource names are available in the target region

### Step-by-Step Deployment

#### 1. Prepare Parameters

Edit the `azuredeploy.parameters.json` file with your specific values:

```bash
# Copy the template parameters file
cp azuredeploy.parameters.json azuredeploy.parameters.prod.json

# Edit the parameters file
nano azuredeploy.parameters.prod.json
```

#### 2. Deploy Infrastructure

**Using Azure CLI:**
```bash
# Make the script executable
chmod +x deploy.sh

# Run the deployment
./deploy.sh -s "your-subscription-id" -g "assets-manager-prod-rg" -l "East US" -e "prod"
```

**Using PowerShell:**
```powershell
# Run the deployment
./deploy.ps1 -SubscriptionId "your-subscription-id" -ResourceGroupName "assets-manager-prod-rg" -Location "East US" -Environment "prod"
```

#### 3. Initialize Database

```bash
# Connect to the Azure PostgreSQL database
psql "host=assets-manager-prod-db.postgres.database.azure.com port=5432 dbname=assets_manager user=adminuser@assets-manager-prod-db sslmode=require"

# Run the initialization script
\i init-database.sql
```

#### 4. Configure Key Vault Secrets

```powershell
# Set up Key Vault secrets
./setup-keyvault.ps1 -KeyVaultName "assets-manager-prod-keyvault" -DatabasePassword "your-secure-password" -StorageConnectionString "your-storage-connection-string" -ServiceBusConnectionString "your-servicebus-connection-string"
```

#### 5. Deploy Applications

```bash
# Build and deploy the applications (using Azure CLI)
mvn clean package

# Deploy web application
az webapp deploy --resource-group assets-manager-prod-rg --name assets-manager-prod-web --src-path ./web/target/assets-manager-web-0.0.1-SNAPSHOT.jar

# Deploy worker application
az webapp deploy --resource-group assets-manager-prod-rg --name assets-manager-prod-worker --src-path ./worker/target/assets-manager-worker-0.0.1-SNAPSHOT.jar
```

This Infrastructure as Code approach ensures consistent, repeatable deployments and helps maintain environment parity across development, staging, and production environments.