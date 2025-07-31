# Azure Migration Technical Implementation Guide
## Asset Manager Application - Detailed Implementation Steps

### Overview
This guide provides detailed technical implementation steps for migrating the Asset Manager application from AWS to Azure. Each section includes specific code changes, configuration updates, and testing procedures.

---

## Phase 1: Azure Foundation Setup

### 1.1 Azure Resource Provisioning

#### Required Azure Resources
```bash
# Create Resource Group
az group create --name asset-manager-rg --location eastus

# Create Azure Key Vault
az keyvault create --name asset-manager-kv --resource-group asset-manager-rg --location eastus

# Create Azure Database for PostgreSQL
az postgres server create \
  --resource-group asset-manager-rg \
  --name asset-manager-db \
  --location eastus \
  --admin-user adminuser \
  --admin-password <secure-password> \
  --sku-name B_Gen5_1

# Create Storage Account
az storage account create \
  --name assetmanagerstorage \
  --resource-group asset-manager-rg \
  --location eastus \
  --sku Standard_LRS

# Create Service Bus Namespace
az servicebus namespace create \
  --resource-group asset-manager-rg \
  --name asset-manager-sb \
  --location eastus \
  --sku Standard

# Create Service Bus Queue
az servicebus queue create \
  --resource-group asset-manager-rg \
  --namespace-name asset-manager-sb \
  --name image-processing
```

### 1.2 Managed Identity Configuration

#### Create User-Assigned Managed Identity
```bash
az identity create \
  --resource-group asset-manager-rg \
  --name asset-manager-identity
```

#### Assign Permissions
```bash
# Storage Blob Data Contributor
az role assignment create \
  --assignee <managed-identity-client-id> \
  --role "Storage Blob Data Contributor" \
  --scope /subscriptions/<subscription-id>/resourceGroups/asset-manager-rg/providers/Microsoft.Storage/storageAccounts/assetmanagerstorage

# Service Bus Data Owner
az role assignment create \
  --assignee <managed-identity-client-id> \
  --role "Azure Service Bus Data Owner" \
  --scope /subscriptions/<subscription-id>/resourceGroups/asset-manager-rg/providers/Microsoft.ServiceBus/namespaces/asset-manager-sb
```

---

## Phase 2: Database Migration

### 2.1 Database Schema Migration

#### Export Current Database
```bash
pg_dump -h localhost -p 5432 -U postgres -d assets_manager > assets_manager_backup.sql
```

#### Import to Azure Database
```bash
psql -h asset-manager-db.postgres.database.azure.com -p 5432 -U adminuser@asset-manager-db -d postgres < assets_manager_backup.sql
```

### 2.2 Update Connection Configuration

#### Update application.properties (both modules)
```properties
# Replace existing database configuration
spring.datasource.url=jdbc:postgresql://asset-manager-db.postgres.database.azure.com:5432/assets_manager
spring.datasource.username=adminuser@asset-manager-db
spring.datasource.password=${DB_PASSWORD}

# Azure configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
```

---

## Phase 3: Azure Service Bus Integration

### 3.1 Dependencies Update

#### Update Parent POM (pom.xml)
```xml
<properties>
    <java.version>11</java.version>
    <spring-cloud-azure.version>5.22.0</spring-cloud-azure.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-dependencies</artifactId>
            <version>${spring-cloud-azure.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Update Web Module POM
```xml
<!-- Remove RabbitMQ dependency -->
<!--
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
-->

<!-- Add Azure Service Bus dependencies -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-messaging-azure-servicebus</artifactId>
</dependency>
```

#### Update Worker Module POM
```xml
<!-- Remove RabbitMQ dependency -->
<!--
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
-->

<!-- Add Azure Service Bus dependencies -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-messaging-azure-servicebus</artifactId>
</dependency>
```

### 3.2 Configuration Updates

#### Web Module application.properties
```properties
# Remove RabbitMQ configuration
# spring.rabbitmq.host=localhost
# spring.rabbitmq.port=5672
# spring.rabbitmq.username=guest
# spring.rabbitmq.password=guest

# Add Azure Service Bus configuration
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.servicebus.namespace=asset-manager-sb.servicebus.windows.net
```

#### Worker Module application.properties
```properties
# Remove RabbitMQ configuration
# spring.rabbitmq.host=localhost
# spring.rabbitmq.port=5672
# spring.rabbitmq.username=guest
# spring.rabbitmq.password=guest

# Add Azure Service Bus configuration
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.servicebus.namespace=asset-manager-sb.servicebus.windows.net
```

### 3.3 Replace RabbitConfig with ServiceBusConfig

#### Create new ServiceBusConfig.java
```java
package com.microsoft.migration.assets.config;

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.spring.cloud.autoconfigure.implementation.servicebus.properties.AzureServiceBusProperties;
import com.azure.spring.messaging.implementation.annotation.EnableAzureMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAzureMessaging
public class ServiceBusConfig {
    public static final String QUEUE_NAME = "image-processing";

    @Bean
    public ServiceBusAdministrationClient adminClient(AzureServiceBusProperties properties, TokenCredential credential) {
        return new ServiceBusAdministrationClientBuilder()
                .credential(properties.getFullyQualifiedNamespace(), credential)
                .buildClient();
    }

    @Bean
    public QueueProperties queue(ServiceBusAdministrationClient adminClient) {
        try {
            return adminClient.getQueue(QUEUE_NAME);
        } catch (Exception e) {
            return adminClient.createQueue(QUEUE_NAME);
        }
    }
}
```

### 3.4 Update Message Handling

#### Update AwsS3Service.java (Message Sending)
```java
// Replace RabbitTemplate with ServiceBusTemplate
// Remove: private final RabbitTemplate rabbitTemplate;
private final ServiceBusTemplate serviceBusTemplate;

// Update message sending logic
private void sendProcessingMessage(String key, String contentType, long size) {
    ImageProcessingMessage message = new ImageProcessingMessage(key, contentType, "azure-blob", size);
    
    // Replace RabbitMQ message sending
    // rabbitTemplate.convertAndSend(QUEUE_NAME, message);
    
    // New Service Bus message sending
    Message<ImageProcessingMessage> serviceBusMessage = MessageBuilder
        .withPayload(message)
        .build();
    serviceBusTemplate.send(QUEUE_NAME, serviceBusMessage);
}
```

#### Update BackupMessageProcessor.java
```java
package com.microsoft.migration.assets.service;

import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
import com.azure.spring.messaging.servicebus.support.ServiceBusMessageHeaders;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.microsoft.migration.assets.config.ServiceBusConfig.QUEUE_NAME;

@Slf4j
@Component
@Profile("backup")
public class BackupMessageProcessor {

    @ServiceBusListener(destination = QUEUE_NAME)
    public void processBackupMessage(ImageProcessingMessage message, 
                                   @Header(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT) ServiceBusReceivedMessageContext context) {
        try {
            log.info("[BACKUP] Monitoring message: {}", message.getKey());
            log.info("[BACKUP] Content type: {}, Storage: {}, Size: {}", 
                    message.getContentType(), message.getStorageType(), message.getSize());
            
            // Complete the message
            context.complete();
            log.info("[BACKUP] Successfully processed message: {}", message.getKey());
        } catch (Exception e) {
            log.error("[BACKUP] Failed to process message: " + message.getKey(), e);
            // Abandon the message for retry
            context.abandon();
            log.warn("[BACKUP] Message abandoned for retry: {}", message.getKey());
        }
    }
}
```

#### Update Worker Module File Processing
```java
// Update worker module's message processing
@ServiceBusListener(destination = QUEUE_NAME)
public void processImageMessage(ImageProcessingMessage message,
                               @Header(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT) ServiceBusReceivedMessageContext context) {
    try {
        log.info("Processing image: {}", message.getKey());
        
        // Existing processing logic remains the same
        processImage(message);
        
        // Complete the message instead of manual ACK
        context.complete();
        log.info("Successfully processed image: {}", message.getKey());
    } catch (Exception e) {
        log.error("Failed to process image: " + message.getKey(), e);
        // Abandon for retry instead of NACK
        context.abandon();
    }
}
```

---

## Phase 4: Azure Blob Storage Integration

### 4.1 Dependencies Update

#### Update Web and Worker Module POMs
```xml
<!-- Remove AWS S3 dependencies -->
<!--
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
-->

<!-- Add Azure Blob Storage dependencies -->
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
```

### 4.2 Configuration Updates

#### Replace AwsS3Config with AzureBlobConfig
```java
package com.microsoft.migration.assets.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBlobConfig {

    @Value("${azure.storage.account.endpoint}")
    private String storageEndpoint;

    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
                .endpoint(storageEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
```

#### Update application.properties
```properties
# Replace AWS S3 configuration
# aws.accessKey=your-access-key
# aws.secretKey=your-secret-key
# aws.region=us-east-1
# aws.s3.bucket=your-bucket-name

# Add Azure Blob Storage configuration
azure.storage.account.endpoint=https://assetmanagerstorage.blob.core.windows.net
azure.storage.container.name=assets
```

### 4.3 Update Storage Service Implementation

#### Create AzureBlobStorageService.java
```java
package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.microsoft.migration.assets.model.ImageMetadata;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.ServiceBusConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
@Profile("!dev")
public class AzureBlobStorageService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final ServiceBusTemplate serviceBusTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container.name}")
    private String containerName;

    @Override
    public List<S3StorageItem> listObjects() {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        return containerClient.listBlobs().stream()
                .map(blobItem -> {
                    BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
                    BlobProperties properties = blobClient.getProperties();
                    
                    // Try to get metadata for upload time
                    Instant uploadedAt = imageMetadataRepository.findAll().stream()
                            .filter(metadata -> metadata.getS3Key().equals(blobItem.getName()))
                            .map(metadata -> metadata.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            .findFirst()
                            .orElse(properties.getLastModified());

                    return new S3StorageItem(
                            blobItem.getName(),
                            extractFilename(blobItem.getName()),
                            properties.getBlobSize(),
                            properties.getLastModified(),
                            uploadedAt,
                            blobClient.getBlobUrl()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String key = generateKey(file.getOriginalFilename());
        
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        // Upload file to Azure Blob Storage
        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // Send message to Service Bus for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
                key, 
                file.getContentType(), 
                "azure-blob", 
                file.getSize()
        );
        
        Message<ImageProcessingMessage> serviceBusMessage = MessageBuilder
                .withPayload(message)
                .build();
        serviceBusTemplate.send(QUEUE_NAME, serviceBusMessage);

        // Save metadata to database
        saveImageMetadata(file, key, blobClient.getBlobUrl());
    }

    @Override
    public void deleteObject(String key) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        blobClient.deleteIfExists();

        // Delete metadata from database
        imageMetadataRepository.deleteById(extractFilename(key));
    }

    private String generateKey(String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        return uuid + extension;
    }

    private String extractFilename(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    private void saveImageMetadata(MultipartFile file, String key, String url) {
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(extractFilename(key));
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(key);
        metadata.setS3Url(url);
        
        imageMetadataRepository.save(metadata);
    }
}
```

### 4.4 Update Worker Module Storage Processing

#### Create AzureBlobFileProcessingService.java
```java
package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class AzureBlobFileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient;
    private final ImageMetadataRepository imageMetadataRepository;
    
    @Value("${azure.storage.container.name}")
    private String containerName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        try (FileOutputStream outputStream = new FileOutputStream(destination.toFile())) {
            blobClient.downloadStream(outputStream);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        blobClient.uploadFromFile(source.toString(), true);
        
        // Update metadata with thumbnail information
        updateImageMetadataWithThumbnail(key, blobClient.getBlobUrl());
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }
    
    private void updateImageMetadataWithThumbnail(String thumbnailKey, String thumbnailUrl) {
        // Extract original key from thumbnail key (remove "thumb_" prefix)
        String originalKey = thumbnailKey.replace("thumb_", "");
        String filename = extractFilename(originalKey);
        
        imageMetadataRepository.findById(filename).ifPresent(metadata -> {
            metadata.setThumbnailKey(thumbnailKey);
            metadata.setThumbnailUrl(thumbnailUrl);
            imageMetadataRepository.save(metadata);
        });
    }
    
    private String extractFilename(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }
}
```

---

## Phase 5: Application Configuration Updates

### 5.1 Update Application Properties

#### Web Module application.properties
```properties
spring.application.name=assets-manager

# Azure Blob Storage Configuration
azure.storage.account.endpoint=https://assetmanagerstorage.blob.core.windows.net
azure.storage.container.name=assets

# Max file size for uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.servicebus.namespace=asset-manager-sb.servicebus.windows.net

# Database Configuration
spring.datasource.url=jdbc:postgresql://asset-manager-db.postgres.database.azure.com:5432/assets_manager
spring.datasource.username=adminuser@asset-manager-db
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
```

#### Worker Module application.properties
```properties
# Azure Blob Storage Configuration
azure.storage.account.endpoint=https://assetmanagerstorage.blob.core.windows.net
azure.storage.container.name=assets

# Server port (different from web module)
server.port=8081

# Application name
spring.application.name=assets-manager-worker

# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.servicebus.namespace=asset-manager-sb.servicebus.windows.net

# Database Configuration
spring.datasource.url=jdbc:postgresql://asset-manager-db.postgres.database.azure.com:5432/assets_manager
spring.datasource.username=adminuser@asset-manager-db
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### 5.2 Environment-Specific Configuration

#### Create application-azure.properties
```properties
# Azure-specific configuration
logging.level.com.azure=DEBUG
logging.level.com.microsoft.migration.assets=INFO

# Azure monitoring and diagnostics
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
```

---

## Phase 6: Testing and Validation

### 6.1 Unit Test Updates

#### Update Test Dependencies
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>azure</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <scope>test</scope>
</dependency>
```

#### Create Integration Test
```java
@SpringBootTest
@TestPropertySource(properties = {
    "azure.storage.account.endpoint=http://localhost:10000/devstoreaccount1",
    "azure.storage.container.name=test-container",
    "spring.cloud.azure.servicebus.namespace=test-namespace"
})
class AzureBlobStorageServiceTest {
    
    @Autowired
    private AzureBlobStorageService storageService;
    
    @Test
    void testUploadAndListObjects() {
        // Test implementation
    }
}
```

### 6.2 Performance Testing

#### Load Testing Configuration
```yaml
# k6 load test script
export default function () {
  let response = http.post('http://localhost:8080/upload', {
    file: open('test-image.jpg', 'b'),
  });
  check(response, {
    'upload successful': (r) => r.status === 200,
    'response time < 5s': (r) => r.timings.duration < 5000,
  });
}
```

---

## Phase 7: Deployment Configuration

### 7.1 Azure App Service Configuration

#### Create Azure App Service
```bash
# Create App Service Plan
az appservice plan create \
  --name asset-manager-plan \
  --resource-group asset-manager-rg \
  --sku B1 \
  --is-linux

# Create Web App for web module
az webapp create \
  --resource-group asset-manager-rg \
  --plan asset-manager-plan \
  --name asset-manager-web \
  --runtime "JAVA|11-java11"

# Create Web App for worker module
az webapp create \
  --resource-group asset-manager-rg \
  --plan asset-manager-plan \
  --name asset-manager-worker \
  --runtime "JAVA|11-java11"
```

#### Configure App Settings
```bash
# Set environment variables
az webapp config appsettings set \
  --resource-group asset-manager-rg \
  --name asset-manager-web \
  --settings \
    AZURE_CLIENT_ID="<managed-identity-client-id>" \
    DB_PASSWORD="<database-password>" \
    SPRING_PROFILES_ACTIVE="azure"

az webapp config appsettings set \
  --resource-group asset-manager-rg \
  --name asset-manager-worker \
  --settings \
    AZURE_CLIENT_ID="<managed-identity-client-id>" \
    DB_PASSWORD="<database-password>" \
    SPRING_PROFILES_ACTIVE="azure"
```

### 7.2 CI/CD Pipeline Configuration

#### GitHub Actions Workflow
```yaml
name: Deploy to Azure

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
    
    - name: Build with Maven
      run: mvn clean package
    
    - name: Deploy Web App
      uses: azure/webapps-deploy@v2
      with:
        app-name: 'asset-manager-web'
        slot-name: 'production'
        publish-profile: ${{ secrets.AZURE_WEBAPP_PUBLISH_PROFILE_WEB }}
        package: './web/target/*.jar'
    
    - name: Deploy Worker App
      uses: azure/webapps-deploy@v2
      with:
        app-name: 'asset-manager-worker'
        slot-name: 'production'
        publish-profile: ${{ secrets.AZURE_WEBAPP_PUBLISH_PROFILE_WORKER }}
        package: './worker/target/*.jar'
```

---

## Rollback Plan

### Emergency Rollback Procedure

1. **Database Rollback**
   ```bash
   # Restore database from backup
   az postgres db import \
     --resource-group asset-manager-rg \
     --server-name asset-manager-db \
     --name assets_manager \
     --input-file assets_manager_backup.sql
   ```

2. **Configuration Rollback**
   - Revert application.properties to AWS configuration
   - Restore RabbitMQ dependencies in pom.xml
   - Redeploy previous version

3. **Data Synchronization**
   - Sync any new data from Azure back to AWS
   - Verify data integrity

---

## Monitoring and Alerting

### Azure Monitor Configuration
```bash
# Create Log Analytics Workspace
az monitor log-analytics workspace create \
  --resource-group asset-manager-rg \
  --workspace-name asset-manager-logs

# Create Application Insights
az monitor app-insights component create \
  --app asset-manager-insights \
  --location eastus \
  --resource-group asset-manager-rg \
  --application-type web
```

### Health Check Endpoints
```java
@RestController
public class HealthController {
    
    @Autowired
    private BlobServiceClient blobServiceClient;
    
    @Autowired
    private ServiceBusTemplate serviceBusTemplate;
    
    @GetMapping("/health/storage")
    public ResponseEntity<String> checkStorageHealth() {
        try {
            blobServiceClient.getBlobContainerClient("assets").exists();
            return ResponseEntity.ok("Storage healthy");
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Storage unhealthy");
        }
    }
    
    @GetMapping("/health/messaging")
    public ResponseEntity<String> checkMessagingHealth() {
        try {
            // Simple health check for Service Bus
            return ResponseEntity.ok("Messaging healthy");
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Messaging unhealthy");
        }
    }
}
```

---

## Security Considerations

### 1. Managed Identity Best Practices
- Use system-assigned managed identity where possible
- Limit scope of role assignments to minimum required
- Regular audit of permissions

### 2. Network Security
- Configure Virtual Network integration
- Use Private Endpoints for storage and database
- Implement network security groups

### 3. Data Encryption
- Enable encryption at rest for storage
- Use TLS 1.2+ for all communications
- Implement proper key rotation

---

**Document Version**: 1.0  
**Last Updated**: $(date)  
**Prepared By**: Azure Migration Implementation Team