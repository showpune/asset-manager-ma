# Azure Migration Technical Implementation Guide
## Asset Manager Application

This document provides specific code examples and technical steps for migrating from AWS to Azure services.

---

## Phase 1: Maven Dependencies Update

### Remove AWS Dependencies
```xml
<!-- Remove these AWS dependencies -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.13</version>
</dependency>
```

### Add Azure Dependencies
```xml
<!-- Add Azure Storage -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.29.0</version>
</dependency>

<!-- Add Azure Identity -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.15.4</version>
</dependency>

<!-- Add Azure Service Bus -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-messaging-azure-servicebus</artifactId>
</dependency>

<!-- Add Managed Dependency -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-dependencies</artifactId>
            <version>5.22.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Phase 2: Storage Migration (S3 → Azure Blob)

### 1. Replace AwsS3Config.java
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

    @Value("${azure.storage.account.name}")
    private String storageAccountName;

    @Bean
    public BlobServiceClient blobServiceClient() {
        String endpoint = String.format("https://%s.blob.core.windows.net", storageAccountName);
        
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
```

### 2. Replace AwsS3Service.java
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Profile("!dev")
public class AzureBlobStorageService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final RabbitTemplate rabbitTemplate; // Will be replaced with Service Bus
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
                    
                    // Get metadata for upload time
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
        
        // Upload with metadata
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        
        // Send message for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
            key,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        rabbitTemplate.convertAndSend("image-processing", message); // Will be replaced
        
        // Save metadata to database
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(key);
        metadata.setS3Url(blobClient.getBlobUrl());
        
        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        return blobClient.downloadContent().toStream();
    }

    @Override
    public void deleteObject(String key) throws IOException {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        // Delete blob
        blobClient.deleteIfExists();
        
        // Try to delete thumbnail if it exists
        try {
            BlobClient thumbnailClient = containerClient.getBlobClient(getThumbnailKey(key));
            thumbnailClient.deleteIfExists();
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist
        }
        
        // Delete metadata from database
        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(key))
                .findFirst()
                .ifPresent(metadata -> imageMetadataRepository.delete(metadata));
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }

    private String extractFilename(String key) {
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }

    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
    
    private String getThumbnailKey(String originalKey) {
        return "thumbnails/" + originalKey;
    }
}
```

### 3. Update Configuration Properties
```properties
# Replace AWS S3 configuration with Azure Blob Storage
azure.storage.account.name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container.name=${AZURE_STORAGE_CONTAINER_NAME:assets}

# Remove AWS configuration
# aws.accessKey=your-access-key
# aws.secretKey=your-secret-key
# aws.region=us-east-1
# aws.s3.bucket=your-bucket-name
```

---

## Phase 3: Messaging Migration (RabbitMQ → Service Bus)

### 1. Replace RabbitConfig.java
```java
package com.microsoft.migration.assets.config;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.spring.cloud.autoconfigure.implementation.servicebus.properties.AzureServiceBusProperties;
import com.azure.spring.messaging.implementation.annotation.EnableAzureMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAzureMessaging
public class AzureServiceBusConfig {

    public static final String QUEUE_NAME = "image-processing";

    @Bean
    public ServiceBusAdministrationClient adminClient(
            AzureServiceBusProperties properties, 
            TokenCredential credential) {
        return new ServiceBusAdministrationClientBuilder()
                .credential(properties.getFullyQualifiedNamespace(), credential)
                .buildClient();
    }

    @Bean
    public QueueProperties imageProcessingQueue(ServiceBusAdministrationClient adminClient) {
        try {
            return adminClient.getQueue(QUEUE_NAME);
        } catch (ResourceNotFoundException e) {
            return adminClient.createQueue(QUEUE_NAME);
        }
    }
}
```

### 2. Update Message Publisher (Web Module)
```java
package com.microsoft.migration.assets.service;

import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import static com.microsoft.migration.assets.config.AzureServiceBusConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
public class ImageProcessingMessagePublisher {
    
    private final ServiceBusTemplate serviceBusTemplate;
    
    public void publishMessage(ImageProcessingMessage processingMessage) {
        Message<ImageProcessingMessage> message = MessageBuilder
                .withPayload(processingMessage)
                .build();
                
        serviceBusTemplate.send(QUEUE_NAME, message);
    }
}
```

### 3. Update Message Consumer (Worker Module)
```java
package com.microsoft.migration.assets.worker.service;

import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.microsoft.migration.assets.config.AzureServiceBusConfig.QUEUE_NAME;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingMessageConsumer {
    
    private final FileProcessor fileProcessor;
    
    @ServiceBusListener(destination = QUEUE_NAME)
    public void processImageMessage(ImageProcessingMessage message) {
        log.info("Processing image: {}", message.getKey());
        
        try {
            fileProcessor.processFile(message);
            log.info("Successfully processed image: {}", message.getKey());
        } catch (Exception e) {
            log.error("Failed to process image: {}", message.getKey(), e);
            // Handle error - could implement retry logic
        }
    }
}
```

### 4. Update Service Bus Configuration Properties
```properties
# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.namespace=${SERVICE_BUS_NAMESPACE}
spring.cloud.azure.servicebus.entity-type=queue

# Remove RabbitMQ configuration
# spring.rabbitmq.host=localhost
# spring.rabbitmq.port=5672
# spring.rabbitmq.username=guest
# spring.rabbitmq.password=guest
```

### 5. Update AzureBlobStorageService to use Service Bus
```java
// Replace the RabbitTemplate injection with ServiceBusTemplate
private final ServiceBusTemplate serviceBusTemplate;

// Update the publishMessage call in uploadObject method
public void uploadObject(MultipartFile file) throws IOException {
    // ... existing code ...
    
    // Send message for thumbnail generation using Service Bus
    ImageProcessingMessage message = new ImageProcessingMessage(
        key,
        file.getContentType(),
        getStorageType(),
        file.getSize()
    );
    
    Message<ImageProcessingMessage> busMessage = MessageBuilder
            .withPayload(message)
            .build();
    serviceBusTemplate.send(QUEUE_NAME, busMessage);
    
    // ... rest of the method ...
}
```

---

## Phase 4: Database Migration

### 1. Update Database Configuration
```properties
# Azure Database for PostgreSQL
spring.datasource.url=jdbc:postgresql://${AZURE_POSTGRES_SERVER}.postgres.database.azure.com:5432/${AZURE_POSTGRES_DATABASE}?sslmode=require
spring.datasource.username=${AZURE_POSTGRES_USERNAME}
spring.datasource.password=${AZURE_POSTGRES_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=false
```

### 2. Database Migration Script
```bash
#!/bin/bash
# migrate-database.sh

# Export from current PostgreSQL
pg_dump -h localhost -U postgres -d assets_manager > assets_manager_backup.sql

# Import to Azure PostgreSQL
psql -h ${AZURE_POSTGRES_SERVER}.postgres.database.azure.com \
     -U ${AZURE_POSTGRES_USERNAME} \
     -d ${AZURE_POSTGRES_DATABASE} \
     -f assets_manager_backup.sql
```

---

## Phase 5: Application Deployment

### 1. Create Azure App Service Deployment Configuration
```yaml
# azure-pipelines.yml
trigger:
- main

pool:
  vmImage: 'ubuntu-latest'

variables:
  mavenVersion: '3.8.1'
  javaVersion: '11'

stages:
- stage: Build
  jobs:
  - job: BuildJob
    steps:
    - task: JavaToolInstaller@0
      inputs:
        versionSpec: $(javaVersion)
        jdkArchitectureOption: 'x64'
        jdkSourceOption: 'PreInstalled'
    
    - task: Maven@3
      inputs:
        mavenPomFile: 'pom.xml'
        goals: 'clean package'
        options: '-DskipTests'
        javaHomeOption: 'JDKVersion'
        jdkVersionOption: $(javaVersion)
    
    - task: PublishBuildArtifacts@1
      inputs:
        pathtoPublish: 'web/target'
        artifactName: 'web-app'
    
    - task: PublishBuildArtifacts@1
      inputs:
        pathtoPublish: 'worker/target'
        artifactName: 'worker-app'

- stage: Deploy
  dependsOn: Build
  jobs:
  - deployment: DeployWeb
    environment: 'production'
    strategy:
      runOnce:
        deploy:
          steps:
          - task: AzureWebApp@1
            inputs:
              azureSubscription: 'Azure-Connection'
              appType: 'webAppLinux'
              appName: 'asset-manager-web'
              package: '$(Pipeline.Workspace)/web-app/*.jar'
              runtimeStack: 'JAVA|11-java11'
  
  - deployment: DeployWorker
    environment: 'production'
    strategy:
      runOnce:
        deploy:
          steps:
          - task: AzureWebApp@1
            inputs:
              azureSubscription: 'Azure-Connection'
              appType: 'webAppLinux'
              appName: 'asset-manager-worker'
              package: '$(Pipeline.Workspace)/worker-app/*.jar'
              runtimeStack: 'JAVA|11-java11'
```

### 2. Azure App Service Configuration
```json
{
  "name": "asset-manager-web",
  "properties": {
    "siteConfig": {
      "appSettings": [
        {
          "name": "AZURE_STORAGE_ACCOUNT_NAME",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=storage-account-name)"
        },
        {
          "name": "AZURE_CLIENT_ID",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=client-id)"
        },
        {
          "name": "SERVICE_BUS_NAMESPACE",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=servicebus-namespace)"
        },
        {
          "name": "AZURE_POSTGRES_SERVER",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=postgres-server)"
        },
        {
          "name": "AZURE_POSTGRES_DATABASE",
          "value": "assets_manager"
        },
        {
          "name": "AZURE_POSTGRES_USERNAME",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=postgres-username)"
        },
        {
          "name": "AZURE_POSTGRES_PASSWORD",
          "value": "@Microsoft.KeyVault(VaultName=asset-manager-kv;SecretName=postgres-password)"
        }
      ],
      "javaVersion": "11",
      "linuxFxVersion": "JAVA|11-java11"
    },
    "httpsOnly": true,
    "identity": {
      "type": "UserAssigned",
      "userAssignedIdentities": {
        "/subscriptions/{subscription-id}/resourcegroups/asset-manager-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/asset-manager-identity": {}
      }
    }
  }
}
```

---

## Phase 6: Monitoring and Observability

### 1. Add Application Insights Dependencies
```xml
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-monitor</artifactId>
</dependency>
```

### 2. Application Insights Configuration
```java
package com.microsoft.migration.assets.config;

import com.azure.monitor.applicationinsights.spring.TelemetryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfiguration {
    
    @Bean
    public TelemetryClient telemetryClient() {
        return new TelemetryClient();
    }
}
```

### 3. Custom Metrics
```java
package com.microsoft.migration.assets.service;

import com.azure.monitor.applicationinsights.spring.TelemetryClient;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    
    private final TelemetryClient telemetryClient;
    
    public MetricsService(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
    }
    
    public void trackFileUpload(String fileName, long fileSize, boolean success) {
        telemetryClient.trackEvent("FileUpload", 
            Map.of(
                "fileName", fileName,
                "fileSize", String.valueOf(fileSize),
                "success", String.valueOf(success)
            ),
            Map.of("fileSize", (double) fileSize)
        );
    }
    
    public void trackThumbnailGeneration(String key, long processingTime) {
        telemetryClient.trackDependency("ThumbnailGeneration", key, 
            Instant.now().minusMillis(processingTime), 
            Duration.ofMillis(processingTime), 
            true);
    }
}
```

### 4. Application Insights Properties
```properties
# Application Insights Configuration
azure.monitor.applicationinsights.instrumentation-key=${APPLICATIONINSIGHTS_INSTRUMENTATION_KEY}
azure.monitor.applicationinsights.connection-string=${APPLICATIONINSIGHTS_CONNECTION_STRING}
```

---

## Testing Strategy

### 1. Unit Tests for Azure Services
```java
package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AzureBlobStorageServiceTest {

    @Mock
    private BlobServiceClient blobServiceClient;
    
    @Mock
    private BlobContainerClient containerClient;
    
    @Mock
    private BlobClient blobClient;
    
    @Mock
    private MultipartFile multipartFile;
    
    private AzureBlobStorageService storageService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storageService = new AzureBlobStorageService(blobServiceClient, null, null);
        
        when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
    }
    
    @Test
    void testUploadObject() throws Exception {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getInputStream()).thenReturn(mock(InputStream.class));
        when(blobClient.getBlobUrl()).thenReturn("https://test.blob.core.windows.net/test.jpg");
        
        // When
        storageService.uploadObject(multipartFile);
        
        // Then
        verify(blobClient).upload(any(InputStream.class), eq(1024L), eq(true));
    }
}
```

### 2. Integration Tests
```java
package com.microsoft.migration.assets.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "azure.storage.account.name=test",
    "azure.storage.container.name=test",
    "spring.cloud.azure.servicebus.namespace=test"
})
class AzureIntegrationTest {
    
    @Test
    void contextLoads() {
        // Test that Spring context loads with Azure configuration
    }
}
```

---

## Rollback Strategy

### 1. Immediate Rollback Script
```bash
#!/bin/bash
# rollback.sh

echo "Rolling back to AWS configuration..."

# Switch DNS back to old servers
az network dns record-set a update --resource-group dns-rg --zone-name example.com --name web --set aRecords[0].ipv4Address=OLD_IP

# Scale down Azure resources
az appservice plan update --name web-plan --resource-group asset-manager-rg --sku FREE

# Re-enable AWS resources if needed
aws ec2 start-instances --instance-ids i-1234567890abcdef0

echo "Rollback completed"
```

### 2. Configuration Rollback
```properties
# Keep both configurations and switch via profiles
spring.profiles.active=aws  # Change to 'azure' for new setup

# AWS Configuration (rollback)
spring.cloud.aws.credentials.access-key=${AWS_ACCESS_KEY}
spring.cloud.aws.credentials.secret-key=${AWS_SECRET_KEY}
spring.cloud.aws.region.static=${AWS_REGION}
spring.cloud.aws.s3.bucket=${AWS_S3_BUCKET}

# Azure Configuration (new)
azure.storage.account.name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container.name=${AZURE_STORAGE_CONTAINER_NAME}
```

---

## Validation Checklist

### Pre-Go-Live Validation
- [ ] All Azure resources provisioned and configured
- [ ] Database migration completed and validated
- [ ] File upload/download functionality working
- [ ] Thumbnail generation processing correctly
- [ ] Message queuing functioning properly
- [ ] Authentication with Managed Identity working
- [ ] Monitoring and logging operational
- [ ] Performance meets baseline requirements
- [ ] Security scanning passed
- [ ] Backup and recovery procedures tested

### Post-Go-Live Monitoring
- [ ] Application performance metrics normal
- [ ] Error rates within acceptable limits
- [ ] User functionality working as expected
- [ ] Cost monitoring active
- [ ] Security monitoring active
- [ ] Rollback procedures ready if needed

This technical guide provides the concrete implementation steps needed to successfully migrate the Asset Manager application from AWS to Azure services.