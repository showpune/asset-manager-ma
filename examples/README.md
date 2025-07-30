# Azure Migration Configuration Examples

This directory contains example implementations for migrating the Asset Manager application from AWS to Azure.

## Files Overview

### 1. `AzureBlobStorageService.java`
- **Purpose**: Replaces `AwsS3Service.java` 
- **Changes**: 
  - Uses Azure Blob Storage SDK instead of AWS S3 SDK
  - Implements Azure Managed Identity for authentication
  - Maintains backward compatibility with existing interfaces
  - Generates SAS URLs for secure blob access

### 2. `AzureBlobStorageConfig.java`
- **Purpose**: Replaces `AwsS3Config.java`
- **Changes**:
  - Configures Azure Blob Storage client with Managed Identity
  - Creates blob container if it doesn't exist
  - Uses environment variables for configuration

### 3. `AzureServiceBusConfig.java`
- **Purpose**: Replaces `RabbitConfig.java`
- **Changes**:
  - Configures Azure Service Bus with Managed Identity
  - Creates queues automatically if they don't exist
  - Enables Azure messaging annotations

### 4. `AzureImageProcessingListener.java`
- **Purpose**: Updates message listener for Azure Service Bus
- **Changes**:
  - Uses `@ServiceBusListener` instead of `@RabbitListener`
  - Implements Azure Service Bus message context handling
  - Maintains error handling and retry logic

## Configuration Changes Required

### Maven Dependencies (pom.xml)

Remove AWS dependencies:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>${aws-sdk.version}</version>
</dependency>
```

Add Azure dependencies:
```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.29.0</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.15.4</version>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-messaging-azure-servicebus</artifactId>
</dependency>
```

### Application Properties

Remove AWS configuration:
```properties
aws.accessKey=your-access-key
aws.secretKey=your-secret-key
aws.region=us-east-1
aws.s3.bucket=your-bucket-name

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

Add Azure configuration:
```properties
# Azure Storage Configuration
azure.storage.account-name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container-name=${AZURE_STORAGE_CONTAINER_NAME}

# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.servicebus.namespace=${SERVICE_BUS_NAMESPACE}

# Database Configuration (minimal changes)
spring.datasource.url=jdbc:postgresql://${AZURE_DB_HOST}:5432/assets_manager
spring.datasource.username=${AZURE_DB_USERNAME}
spring.datasource.password=${AZURE_DB_PASSWORD}
```

## Implementation Steps

1. **Update Dependencies**: Replace AWS dependencies with Azure equivalents in both web and worker modules
2. **Update Configuration**: Replace AWS configuration with Azure configuration
3. **Replace Services**: Replace `AwsS3Service` with `AzureBlobStorageService`
4. **Update Config Classes**: Replace `AwsS3Config` and `RabbitConfig` with Azure equivalents
5. **Update Listeners**: Replace RabbitMQ listeners with Azure Service Bus listeners
6. **Test Integration**: Verify all functionality works with Azure services
7. **Deploy**: Deploy to Azure Container Apps or Azure Kubernetes Service

## Key Benefits of Migration

- **Managed Identity**: No need to manage access keys or secrets
- **Scalability**: Azure services auto-scale based on demand
- **Integration**: Better integration with other Azure services
- **Security**: Enhanced security with Azure AD integration
- **Cost Optimization**: Pay-as-you-use pricing model
- **Monitoring**: Built-in monitoring and alerting capabilities

## Testing Considerations

- Test file upload/download operations
- Verify message processing between modules
- Test error handling and retry logic
- Validate security and access controls
- Performance testing with Azure services
- End-to-end integration testing