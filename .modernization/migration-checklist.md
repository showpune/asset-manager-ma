# Azure Migration Implementation Checklist

## Pre-Migration Setup

### Azure Infrastructure Preparation
- [ ] Create Azure Resource Group
- [ ] Set up Azure Storage Account with Blob containers
  - [ ] Create container for original images
  - [ ] Create container for thumbnails
  - [ ] Configure access policies
- [ ] Create Azure Service Bus namespace
  - [ ] Create queue for image processing messages
  - [ ] Configure dead letter queue
- [ ] Set up Azure Database for PostgreSQL
  - [ ] Configure server firewall rules
  - [ ] Create database and user
- [ ] Create Azure Key Vault
  - [ ] Configure access policies
  - [ ] Set up managed identity if using Azure hosting

### Development Environment
- [ ] Install Azure CLI
- [ ] Install Azure Storage Explorer (optional)
- [ ] Set up local Azure development environment
- [ ] Configure IDE with Azure plugins

## Phase 1: Security & Configuration Migration

### Key Vault Integration
- [ ] Add Azure Key Vault dependency to both modules
```xml
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-keyvault-secrets</artifactId>
</dependency>
```

- [ ] Update application.properties files
```properties
# Replace AWS credentials with Key Vault references
spring.cloud.azure.keyvault.secret.endpoint=https://{vault-name}.vault.azure.net/
```

- [ ] Store secrets in Key Vault
  - [ ] Database connection string
  - [ ] Storage account connection string
  - [ ] Service Bus connection string

### Configuration Files Updates
- [ ] **web/src/main/resources/application.properties**
  - [ ] Remove AWS configuration
  - [ ] Add Azure configuration placeholders
  - [ ] Update database URL format for Azure Database

- [ ] **worker/src/main/resources/application.properties**
  - [ ] Remove AWS configuration
  - [ ] Add Azure configuration placeholders
  - [ ] Update database URL format for Azure Database

## Phase 2: Storage Migration (AWS S3 → Azure Blob Storage)

### Dependencies Update
- [ ] **web/pom.xml** - Replace AWS dependencies
```xml
<!-- Remove -->
<!-- <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency> -->

<!-- Add -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-storage-blob</artifactId>
</dependency>
```

- [ ] **worker/pom.xml** - Apply same dependency changes

### Code Changes - Web Module

#### Configuration Class
- [ ] **web/src/main/java/com/microsoft/migration/assets/config/AwsS3Config.java**
  - [ ] Rename to `AzureBlobConfig.java`
  - [ ] Replace S3Client with BlobServiceClient
  - [ ] Update authentication method (managed identity)
```java
@Configuration
public class AzureBlobConfig {
    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
            .credential(new DefaultAzureCredentialBuilder().build())
            .endpoint("https://{storage-account}.blob.core.windows.net/")
            .buildClient();
    }
}
```

#### Service Implementation
- [ ] **web/src/main/java/com/microsoft/migration/assets/service/AwsS3Service.java**
  - [ ] Rename to `AzureBlobStorageService.java`
  - [ ] Replace S3Client with BlobServiceClient
  - [ ] Update all method implementations
  - [ ] Update URL generation logic

**Critical Methods to Update:**
- [ ] `uploadObject()` - Use BlobClient.upload()
- [ ] `listObjects()` - Use BlobContainerClient.listBlobs()
- [ ] `getObject()` - Use BlobClient.openInputStream()
- [ ] `deleteObject()` - Use BlobClient.delete()
- [ ] `generateUrl()` - Use BlobClient.getBlobUrl()

#### Controller Updates
- [ ] **web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java**
  - [ ] Rename to `StorageController.java` or `BlobController.java`
  - [ ] Update endpoint names (remove S3 references)
  - [ ] Update response models

#### Model Updates
- [ ] **web/src/main/java/com/microsoft/migration/assets/model/S3StorageItem.java**
  - [ ] Rename to `StorageItem.java` or `BlobStorageItem.java`
  - [ ] Remove S3-specific field names
  - [ ] Update constructors and methods

#### Entity Updates
- [ ] **web/src/main/java/com/microsoft/migration/assets/model/ImageMetadata.java**
  - [ ] Rename fields from `s3Key` to `blobKey` or `storageKey`
  - [ ] Rename fields from `s3Url` to `blobUrl` or `storageUrl`
  - [ ] Update getters/setters accordingly

### Code Changes - Worker Module

#### Configuration Class
- [ ] **worker/src/main/java/com/microsoft/migration/assets/worker/config/AwsS3Config.java**
  - [ ] Apply same changes as web module config

#### Service Implementation
- [ ] **worker/src/main/java/com/microsoft/migration/assets/worker/service/S3FileProcessingService.java**
  - [ ] Rename to `BlobFileProcessingService.java`
  - [ ] Replace S3Client with BlobServiceClient
  - [ ] Update method implementations

**Critical Methods to Update:**
- [ ] `downloadOriginal()` - Use BlobClient.downloadToFile()
- [ ] `uploadThumbnail()` - Use BlobClient.uploadFromFile()
- [ ] `generateUrl()` - Use BlobClient.getBlobUrl()

## Phase 3: Messaging Migration (RabbitMQ → Azure Service Bus)

### Dependencies Update
- [ ] **Both modules** - Replace AMQP dependencies
```xml
<!-- Remove -->
<!-- <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency> -->

<!-- Add -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-servicebus-jms</artifactId>
</dependency>
```

### Configuration Updates
- [ ] **web/src/main/java/com/microsoft/migration/assets/config/RabbitConfig.java**
  - [ ] Rename to `ServiceBusConfig.java`
  - [ ] Replace RabbitMQ configuration with Service Bus JMS configuration
```java
@Configuration
public class ServiceBusConfig {
    @Bean
    public ConnectionFactory jmsConnectionFactory() {
        return new ServiceBusJmsConnectionFactory(connectionString, clientBuilder);
    }
}
```

- [ ] **worker/src/main/java/com/microsoft/migration/assets/worker/config/RabbitConfig.java**
  - [ ] Apply same changes as web module

### Message Publishing (Web Module)
- [ ] Update message publishing code
  - [ ] Replace `RabbitTemplate` with `JmsTemplate`
  - [ ] Update queue names and destinations
```java
// Replace rabbitTemplate.convertAndSend() with jmsTemplate.convertAndSend()
jmsTemplate.convertAndSend("image-processing", message);
```

### Message Consuming (Worker Module)
- [ ] Update message listeners
  - [ ] Replace `@RabbitListener` with `@JmsListener`
  - [ ] Update acknowledgment handling
  - [ ] Update error handling patterns
```java
@JmsListener(destination = "image-processing")
public void processMessage(ImageProcessingMessage message) {
    // Process message
}
```

### Backup Message Processor
- [ ] **web/src/main/java/com/microsoft/migration/assets/service/BackupMessageProcessor.java**
  - [ ] Update from RabbitMQ annotations to JMS annotations
  - [ ] Update error handling logic

## Phase 4: Database Migration

### Azure Database Setup
- [ ] Export current PostgreSQL schema
```bash
pg_dump -h localhost -U postgres -s assets_manager > schema.sql
```

- [ ] Export current data
```bash
pg_dump -h localhost -U postgres -a assets_manager > data.sql
```

- [ ] Import to Azure Database for PostgreSQL
- [ ] Verify data integrity

### Application Configuration
- [ ] Update connection strings in Key Vault
```properties
# New Azure Database connection format
jdbc:postgresql://{server}.postgres.database.azure.com:5432/{database}?sslmode=require
```

- [ ] Test connection with managed identity (if applicable)
- [ ] Update firewall rules to allow application access

## Phase 5: Integration & Testing

### Integration Testing
- [ ] Test file upload functionality
- [ ] Test file download functionality
- [ ] Test file listing functionality
- [ ] Test file deletion functionality
- [ ] Test message publishing and consuming
- [ ] Test thumbnail generation workflow
- [ ] Test error handling and retry mechanisms

### Performance Testing
- [ ] Compare upload/download speeds with previous AWS implementation
- [ ] Test concurrent file operations
- [ ] Test message processing throughput
- [ ] Test database query performance

### Security Testing
- [ ] Verify managed identity authentication works
- [ ] Test Key Vault secret retrieval
- [ ] Verify no plaintext credentials in configuration
- [ ] Test access controls and permissions

## Phase 6: Deployment & Monitoring

### Deployment Preparation
- [ ] Create deployment scripts/pipelines
- [ ] Set up environment-specific configurations
- [ ] Prepare rollback procedures

### Optional: Containerization
- [ ] Create Dockerfile for web module
```dockerfile
FROM openjdk:11-jre-slim
COPY target/assets-manager-web.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] Create Dockerfile for worker module
- [ ] Test containerized deployment locally
- [ ] Deploy to Azure Container Apps or AKS

### Monitoring Setup
- [ ] Configure Azure Monitor
- [ ] Set up Application Insights
- [ ] Create alerting rules
- [ ] Set up log aggregation

## Post-Migration Validation

### Functionality Verification
- [ ] All endpoints respond correctly
- [ ] File operations work end-to-end
- [ ] Message processing completes successfully
- [ ] Database operations perform adequately
- [ ] Error scenarios are handled properly

### Documentation Updates
- [ ] Update README with Azure-specific setup instructions
- [ ] Document new environment variables
- [ ] Update deployment procedures
- [ ] Create operational runbook

### Cleanup
- [ ] Remove AWS-specific code comments
- [ ] Clean up unused dependencies
- [ ] Update package names if renamed
- [ ] Remove temporary migration artifacts

## Rollback Plan

### Emergency Rollback Procedure
- [ ] Document steps to revert to AWS implementation
- [ ] Maintain AWS credentials during transition period
- [ ] Keep AWS resources active until migration is proven stable
- [ ] Have database backup restore procedure ready

### Gradual Migration Options
- [ ] Consider blue-green deployment strategy
- [ ] Plan for gradual traffic migration
- [ ] Set up monitoring to compare performance
- [ ] Define success criteria for full cutover

## Success Criteria Checklist

- [ ] Zero data loss during migration
- [ ] All functional tests pass
- [ ] Performance meets or exceeds baseline
- [ ] Security improvements are verified
- [ ] Team is trained on new Azure services
- [ ] Documentation is complete and accurate
- [ ] Monitoring and alerting are functional
- [ ] Rollback procedures are tested and documented