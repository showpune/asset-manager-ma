# Azure Configuration Templates

This directory contains Azure-specific configuration templates to support the migration from AWS to Azure services.

## Files Included

1. `application-azure.properties` - Azure-specific application configuration for both web and worker modules
2. `azure-resources.json` - ARM template for Azure resource provisioning
3. `azure-deploy.yml` - GitHub Actions workflow for Azure deployment
4. `docker-compose-azure.yml` - Docker Compose file for local Azure services testing

## Usage

These templates should be customized with your specific Azure subscription details, resource names, and security configurations before use in production environments.

## Security Notes

- All connection strings and secrets should be stored in Azure Key Vault
- Use Azure Managed Identity for authentication where possible
- Never commit actual credentials or connection strings to source control