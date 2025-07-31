# Azure Migration Implementation Guide

## Overview
This document provides detailed technical implementation steps for migrating the Asset Manager application from AWS to Azure services.

## Pre-Migration Checklist

### Current Application Analysis
- [x] Multi-module Maven Spring Boot application
- [x] Web module (port 8080) - file upload interface
- [x] Worker module (port 8081) - background processing
- [x] AWS S3 for file storage
- [x] RabbitMQ for messaging
- [x] PostgreSQL for metadata storage

### Azure Services Mapping
| Current AWS Service | Azure Equivalent | Migration Priority |
|-------------------|------------------|-------------------|
| AWS S3 | Azure Blob Storage | High |
| AWS SDK v2 | Azure Storage SDK | High |
| RabbitMQ | Azure Service Bus | Medium |
| PostgreSQL | Azure Database for PostgreSQL | Low |
| EC2/Local Deployment | Azure App Service | Medium |

## Step-by-Step Implementation

### Phase 1: Azure Infrastructure Setup

#### 1.1 Create Azure Resources
```bash
# Login to Azure
az login

# Create resource group
az group create --name rg-asset-manager --location eastus

# Create storage account
az storage account create \
    --name stassetmanager001 \
    --resource-group rg-asset-manager \
    --location eastus \
    --sku Standard_LRS \
    --kind StorageV2

# Create blob container
az storage container create \
    --name assets \
    --account-name stassetmanager001 \
    --auth-mode login
```

#### 1.2 Get Connection Information
```bash
# Get connection string
az storage account show-connection-string \
    --name stassetmanager001 \
    --resource-group rg-asset-manager \
    --output tsv
```

### Phase 2: Code Migration

#### 2.1 Update Maven Dependencies

**web/pom.xml changes:**
```xml
<!-- Remove AWS dependencies -->
<!--
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>${aws-sdk.version}</version>
</dependency>
-->

<!-- Add Azure dependencies -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.21.0</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.8.0</version>
</dependency>
```

**worker/pom.xml changes:**
```xml
<!-- Same Azure dependencies as web module -->
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.21.0</version>
</dependency>
```

#### 2.2 Create Azure Configuration Class

**web/src/main/java/com/microsoft/migration/assets/config/AzureStorageConfig.java:**
```java
package com.microsoft.migration.assets.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureStorageConfig {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }
}
```

#### 2.3 Implement Azure Blob Storage Service

**web/src/main/java/com/microsoft/migration/assets/service/AzureBlobService.java:**
```java
package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobClient;
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

import static com.microsoft.migration.assets.config.RabbitConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
public class AzureBlobService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Override
    public List<S3StorageItem> listObjects() {
        return blobServiceClient.getBlobContainerClient(containerName)
                .listBlobs()
                .stream()
                .map(this::convertBlobToStorageItem)
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String blobName = generateBlobName(file.getOriginalFilename());
        
        BlobClient blobClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        
        // Upload file
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        
        // Set content type
        blobClient.setHttpHeaders(new com.azure.storage.blob.models.BlobHttpHeaders()
                .setContentType(file.getContentType()));

        // Send message to queue for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
            blobName,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        rabbitTemplate.convertAndSend(QUEUE_NAME, message);

        // Create and save metadata to database
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(blobName); // Keeping field name for compatibility
        metadata.setS3Url(generateBlobUrl(blobName)); // Keeping field name for compatibility
        
        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String blobName) throws IOException {
        BlobClient blobClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        
        return blobClient.openInputStream();
    }

    @Override
    public void deleteObject(String blobName) throws IOException {
        BlobClient blobClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        
        // Delete blob
        blobClient.deleteIfExists();

        // Try to delete thumbnail if it exists
        String thumbnailBlobName = getThumbnailKey(blobName);
        BlobClient thumbnailClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(thumbnailBlobName);
        thumbnailClient.deleteIfExists();

        // Delete metadata from database
        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(blobName))
                .findFirst()
                .ifPresent(metadata -> imageMetadataRepository.delete(metadata));
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }

    private S3StorageItem convertBlobToStorageItem(BlobItem blobItem) {
        String blobName = blobItem.getName();
        
        // Try to get metadata for upload time
        Instant uploadedAt = imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(blobName))
                .map(metadata -> metadata.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .findFirst()
                .orElse(blobItem.getProperties().getLastModified().toInstant());

        return new S3StorageItem(
                blobName,
                extractFilename(blobName),
                blobItem.getProperties().getContentLength(),
                blobItem.getProperties().getLastModified().toInstant(),
                uploadedAt,
                generateBlobUrl(blobName)
        );
    }

    private String extractFilename(String blobName) {
        int lastSlashIndex = blobName.lastIndexOf('/');
        return lastSlashIndex >= 0 ? blobName.substring(lastSlashIndex + 1) : blobName;
    }
    
    private String generateBlobUrl(String blobName) {
        return blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .getBlobUrl();
    }

    private String generateBlobName(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }

    private String getThumbnailKey(String originalKey) {
        return "thumbnails/" + originalKey;
    }
}
```

#### 2.4 Update Application Properties

**web/src/main/resources/application.properties:**
```properties
spring.application.name=assets-manager

# Azure Storage Configuration
azure.storage.connection-string=DefaultEndpointsProtocol=https;AccountName=stassetmanager001;AccountKey=YOUR_ACCOUNT_KEY;EndpointSuffix=core.windows.net
azure.storage.container-name=assets

# Max file size for uploads (unchanged)
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# RabbitMQ Configuration (unchanged for now)
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Database Configuration (unchanged)
spring.datasource.url=jdbc:postgresql://localhost:5432/assets_manager
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
```

**worker/src/main/resources/application.properties:**
```properties
# Azure Storage Configuration
azure.storage.connection-string=DefaultEndpointsProtocol=https;AccountName=stassetmanager001;AccountKey=YOUR_ACCOUNT_KEY;EndpointSuffix=core.windows.net
azure.storage.container-name=assets

# Server port (different from web module)
server.port=8081

# Application name
spring.application.name=assets-manager-worker

# RabbitMQ Configuration (unchanged for now)
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Database Configuration (unchanged)
spring.datasource.url=jdbc:postgresql://localhost:5432/assets_manager
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### Phase 3: Worker Module Updates

**worker/src/main/java/com/microsoft/migration/assets/worker/service/AzureBlobFileProcessingService.java:**
```java
package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!dev")
public class AzureBlobFileProcessingService implements FileProcessor {

    private final BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Override
    public void processImage(ImageProcessingMessage message) {
        try {
            log.info("Processing image: {}", message.getKey());
            
            // Download original image
            BlobClient originalBlobClient = blobServiceClient
                    .getBlobContainerClient(containerName)
                    .getBlobClient(message.getKey());
            
            InputStream imageStream = originalBlobClient.openInputStream();
            
            // Generate thumbnail
            byte[] thumbnailData = generateThumbnail(imageStream);
            
            // Upload thumbnail
            String thumbnailKey = getThumbnailKey(message.getKey());
            BlobClient thumbnailBlobClient = blobServiceClient
                    .getBlobContainerClient(containerName)
                    .getBlobClient(thumbnailKey);
            
            thumbnailBlobClient.upload(new ByteArrayInputStream(thumbnailData), thumbnailData.length, true);
            
            log.info("Thumbnail created: {}", thumbnailKey);
            
        } catch (Exception e) {
            log.error("Error processing image: {}", message.getKey(), e);
        }
    }

    private byte[] generateThumbnail(InputStream imageStream) throws IOException {
        BufferedImage originalImage = ImageIO.read(imageStream);
        
        // Calculate thumbnail dimensions (max 150x150)
        int thumbnailWidth = 150;
        int thumbnailHeight = 150;
        
        double widthRatio = (double) thumbnailWidth / originalImage.getWidth();
        double heightRatio = (double) thumbnailHeight / originalImage.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);
        
        int scaledWidth = (int) (originalImage.getWidth() * ratio);
        int scaledHeight = (int) (originalImage.getHeight() * ratio);
        
        // Create thumbnail
        BufferedImage thumbnailImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnailImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumbnailImage, "jpg", baos);
        return baos.toByteArray();
    }
    
    private String getThumbnailKey(String originalKey) {
        return "thumbnails/" + originalKey;
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }
}
```

### Phase 4: Testing Strategy

#### 4.1 Unit Tests
Create unit tests for the new Azure Blob Storage service:

**web/src/test/java/com/microsoft/migration/assets/service/AzureBlobServiceTest.java:**
```java
package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClient;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {

    @Mock
    private BlobServiceClient blobServiceClient;
    
    @Mock
    private BlobContainerClient blobContainerClient;
    
    @Mock
    private BlobClient blobClient;
    
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    private AzureBlobService azureBlobService;

    @BeforeEach
    void setUp() {
        azureBlobService = new AzureBlobService(blobServiceClient, rabbitTemplate, imageMetadataRepository);
        ReflectionTestUtils.setField(azureBlobService, "containerName", "test-container");
        
        when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(blobContainerClient);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
    }

    @Test
    void testUploadObject() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );

        // When
        azureBlobService.uploadObject(file);

        // Then
        verify(blobClient).upload(any(), any(Long.class), any(Boolean.class));
        verify(rabbitTemplate).convertAndSend(anyString(), any());
        verify(imageMetadataRepository).save(any());
    }
}
```

#### 4.2 Integration Tests
Create integration tests with Azure Storage emulator or test containers.

### Phase 5: Deployment

#### 5.1 Azure App Service Deployment
Create Azure App Service for both web and worker modules:

```bash
# Create App Service Plan
az appservice plan create \
    --name asp-asset-manager \
    --resource-group rg-asset-manager \
    --sku B1 \
    --is-linux

# Create Web App for web module
az webapp create \
    --name app-asset-manager-web \
    --resource-group rg-asset-manager \
    --plan asp-asset-manager \
    --runtime "JAVA|11-java11"

# Create Web App for worker module
az webapp create \
    --name app-asset-manager-worker \
    --resource-group rg-asset-manager \
    --plan asp-asset-manager \
    --runtime "JAVA|11-java11"
```

#### 5.2 Configuration
Set application settings in Azure App Service:

```bash
# Configure web app settings
az webapp config appsettings set \
    --name app-asset-manager-web \
    --resource-group rg-asset-manager \
    --settings \
    AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;AccountName=..." \
    AZURE_STORAGE_CONTAINER_NAME="assets"
```

## Migration Checklist

- [ ] Azure subscription and resource group created
- [ ] Azure Storage Account created with blob container
- [ ] Maven dependencies updated (AWS SDK → Azure SDK)
- [ ] Azure configuration classes implemented
- [ ] Azure Blob Storage service implemented
- [ ] Application properties updated
- [ ] Worker module updated for Azure Blob Storage
- [ ] Unit tests created and passing
- [ ] Integration tests created and passing
- [ ] Azure App Service created and configured
- [ ] Application deployed to Azure
- [ ] End-to-end testing completed
- [ ] Performance testing completed
- [ ] Production cutover planned

## Rollback Plan

1. Keep AWS environment running during migration
2. Implement feature flags to switch between AWS and Azure
3. Monitor Azure performance and functionality
4. If issues arise, switch traffic back to AWS environment
5. Debug and resolve Azure issues before attempting migration again

## Post-Migration Optimizations

1. Implement Azure CDN for better global performance
2. Set up blob lifecycle policies for cost optimization
3. Integrate with Azure Key Vault for secure configuration
4. Set up Azure Application Insights for monitoring
5. Implement Azure Functions for serverless processing (optional)