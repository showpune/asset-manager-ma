# Technical Implementation Guide

## Implementation Templates and Code Examples

### 1. Java 17 Upgrade Implementation

#### Update Parent POM
```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>17</source>
                <target>17</target>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Module POM Updates
```xml
<dependencies>
    <!-- Remove AWS SDK, add Azure SDK -->
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-starter-storage-blob</artifactId>
    </dependency>
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-starter-servicebus</artifactId>
    </dependency>
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-starter-keyvault-secrets</artifactId>
    </dependency>
</dependencies>
```

### 2. Azure Key Vault Integration

#### Configuration Class
```java
@Configuration
@EnableConfigurationProperties(AzureProperties.class)
public class AzureKeyVaultConfig {
    
    @Bean
    @Primary
    public SecretClient secretClient(AzureProperties azureProperties) {
        return new SecretClientBuilder()
                .vaultUrl(azureProperties.getKeyvault().getVaultUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
```

#### Properties Configuration
```yaml
spring:
  cloud:
    azure:
      keyvault:
        secret:
          endpoint: https://{vault-name}.vault.azure.net/
          property-sources:
            - name: asset-manager-secrets
              endpoint: https://{vault-name}.vault.azure.net/
```

### 3. Azure Blob Storage Implementation

#### Service Implementation
```java
@Service
@RequiredArgsConstructor
@Profile("!dev")
public class AzureBlobStorageService implements StorageService {
    
    private final BlobServiceClient blobServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final ImageMetadataRepository imageMetadataRepository;
    
    @Value("${azure.storage.container.name}")
    private String containerName;
    
    @Override
    public List<S3StorageItem> listObjects() {
        BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
                
        return containerClient.listBlobs().stream()
                .map(blobItem -> {
                    Instant uploadedAt = imageMetadataRepository.findAll().stream()
                            .filter(metadata -> metadata.getS3Key().equals(blobItem.getName()))
                            .map(metadata -> metadata.getUploadedAt()
                                    .atZone(ZoneId.systemDefault()).toInstant())
                            .findFirst()
                            .orElse(blobItem.getProperties().getLastModified().toInstant());
                            
                    return new S3StorageItem(
                            blobItem.getName(),
                            extractFilename(blobItem.getName()),
                            blobItem.getProperties().getContentLength(),
                            blobItem.getProperties().getLastModified().toInstant(),
                            uploadedAt,
                            generateUrl(blobItem.getName())
                    );
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String key = generateKey(file.getOriginalFilename());
        
        BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        // Set content type and metadata
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("originalFileName", file.getOriginalFilename());
        metadata.put("uploadedAt", Instant.now().toString());
        
        // Upload the file
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        blobClient.setHttpHeaders(headers);
        blobClient.setMetadata(metadata);
        
        // Save metadata to database
        ImageMetadata imageMetadata = new ImageMetadata();
        imageMetadata.setS3Key(key);
        imageMetadata.setFilename(file.getOriginalFilename());
        imageMetadata.setContentType(file.getContentType());
        imageMetadata.setFileSize(file.getSize());
        imageMetadata.setUploadedAt(LocalDateTime.now());
        imageMetadata.setS3Url(generateUrl(key));
        
        imageMetadataRepository.save(imageMetadata);
        
        // Send message for thumbnail processing
        ImageProcessingMessage message = new ImageProcessingMessage(key, file.getOriginalFilename());
        rabbitTemplate.convertAndSend(QUEUE_NAME, message);
    }
    
    @Override
    public InputStream getObject(String key) throws IOException {
        BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        try {
            return blobClient.openInputStream();
        } catch (BlobStorageException e) {
            throw new IOException("Failed to retrieve object: " + key, e);
        }
    }
    
    @Override
    public void deleteObject(String key) throws IOException {
        BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        try {
            blobClient.delete();
        } catch (BlobStorageException e) {
            throw new IOException("Failed to delete object: " + key, e);
        }
    }
    
    @Override
    public String getStorageType() {
        return "azure-blob";
    }
    
    private String extractFilename(String key) {
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }
    
    private String generateUrl(String key) {
        BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        return blobClient.getBlobUrl();
    }
    
    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
}
```

#### Configuration
```java
@Configuration
public class AzureBlobStorageConfig {
    
    @Bean
    public BlobServiceClient blobServiceClient(
            @Value("${azure.storage.account.name}") String accountName,
            @Value("${azure.storage.account.key}") String accountKey) {
        
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
        String endpoint = String.format("https://%s.blob.core.windows.net/", accountName);
        
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient();
    }
}
```

### 4. Azure Service Bus Implementation

#### Service Bus Configuration
```java
@Configuration
public class AzureServiceBusConfig {
    
    @Bean
    public ServiceBusProcessorClient serviceProcessorClient(
            @Value("${azure.servicebus.connection-string}") String connectionString,
            @Value("${azure.servicebus.queue.name}") String queueName) {
        
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .processor()
                .queueName(queueName)
                .processMessage(this::processMessage)
                .processError(this::processError)
                .buildProcessorClient();
    }
    
    @Bean
    public ServiceBusSenderClient serviceSenderClient(
            @Value("${azure.servicebus.connection-string}") String connectionString,
            @Value("${azure.servicebus.queue.name}") String queueName) {
        
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
    }
    
    private void processMessage(ServiceBusReceivedMessageContext context) {
        // Process the message
        ServiceBusReceivedMessage message = context.getMessage();
        // Handle message processing logic
        context.complete();
    }
    
    private void processError(ServiceBusErrorContext context) {
        // Handle error
        System.err.println("Error processing message: " + context.getException());
    }
}
```

#### Message Producer
```java
@Component
@RequiredArgsConstructor
public class AzureServiceBusMessageProducer {
    
    private final ServiceBusSenderClient senderClient;
    
    public void sendMessage(ImageProcessingMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(messageBody);
            senderClient.sendMessage(serviceBusMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message", e);
        }
    }
}
```

### 5. PostgreSQL Azure Database Configuration

#### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${azure.postgresql.host}:5432/${azure.postgresql.database}?sslmode=require&serverTimezone=UTC
    username: ${azure.postgresql.username}@${azure.postgresql.server-name}
    password: ${azure.postgresql.password}
  jpa:
    properties:
      hibernate:
        connection:
          provider_disables_autocommit: true
        jdbc:
          batch_size: 25
          order_inserts: true
          order_updates: true
        show_sql: false
    hibernate:
      ddl-auto: validate
```

#### Connection Pool Configuration
```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        return new HikariDataSource(config);
    }
}
```

### 6. Docker Configuration

#### Multi-stage Dockerfile for Web Module
```dockerfile
# Build stage
FROM maven:3.8.6-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY web/pom.xml web/
COPY worker/pom.xml worker/
RUN mvn dependency:go-offline

COPY . .
RUN mvn clean package -DskipTests -pl web -am

# Runtime stage
FROM openjdk:17-jdk-alpine
RUN addgroup -g 1001 -S appuser && adduser -u 1001 -S appuser -G appuser
WORKDIR /app
COPY --from=build /app/web/target/*.jar app.jar
COPY --chown=appuser:appuser --from=build /app/web/target/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

#### Multi-stage Dockerfile for Worker Module  
```dockerfile
# Build stage
FROM maven:3.8.6-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY web/pom.xml web/
COPY worker/pom.xml worker/
RUN mvn dependency:go-offline

COPY . .
RUN mvn clean package -DskipTests -pl worker -am

# Runtime stage
FROM openjdk:17-jdk-alpine
RUN addgroup -g 1001 -S appuser && adduser -u 1001 -S appuser -G appuser
WORKDIR /app
COPY --chown=appuser:appuser --from=build /app/worker/target/*.jar app.jar
USER appuser
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

#### Docker Compose for Development
```yaml
version: '3.8'
services:
  web:
    build:
      context: .
      dockerfile: web/Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/assets_manager
    depends_on:
      - postgres
      - redis
    volumes:
      - ./uploads:/app/uploads
      
  worker:
    build:
      context: .
      dockerfile: worker/Dockerfile
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/assets_manager
    depends_on:
      - postgres
      - redis
      
  postgres:
    image: postgres:13
    environment:
      - POSTGRES_DB=assets_manager
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

### 7. CI/CD Pipeline Configuration

#### GitHub Actions Workflow
```yaml
name: Deploy to Azure

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

env:
  AZURE_CONTAINER_REGISTRY: assetmanageracr.azurecr.io
  AZURE_RESOURCE_GROUP: asset-manager-rg
  AZURE_CONTAINER_APP_NAME: asset-manager-app

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        
    - name: Run tests
      run: mvn clean test
      
    - name: Build application
      run: mvn clean package -DskipTests
      
    - name: Upload build artifacts
      uses: actions/upload-artifact@v3
      with:
        name: jar-artifacts
        path: |
          web/target/*.jar
          worker/target/*.jar

  build-and-push-images:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Download build artifacts
      uses: actions/download-artifact@v3
      with:
        name: jar-artifacts
        
    - name: Azure Login
      uses: azure/login@v1
      with:
        creds: ${{ secrets.AZURE_CREDENTIALS }}
        
    - name: Build and push web image
      uses: azure/docker-login@v1
      with:
        login-server: ${{ env.AZURE_CONTAINER_REGISTRY }}
        username: ${{ secrets.REGISTRY_USERNAME }}
        password: ${{ secrets.REGISTRY_PASSWORD }}
        
    - name: Build and push images
      run: |
        docker build -t ${{ env.AZURE_CONTAINER_REGISTRY }}/asset-manager-web:${{ github.sha }} -f web/Dockerfile .
        docker build -t ${{ env.AZURE_CONTAINER_REGISTRY }}/asset-manager-worker:${{ github.sha }} -f worker/Dockerfile .
        docker push ${{ env.AZURE_CONTAINER_REGISTRY }}/asset-manager-web:${{ github.sha }}
        docker push ${{ env.AZURE_CONTAINER_REGISTRY }}/asset-manager-worker:${{ github.sha }}

  deploy:
    needs: build-and-push-images
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - name: Azure Login
      uses: azure/login@v1
      with:
        creds: ${{ secrets.AZURE_CREDENTIALS }}
        
    - name: Deploy to Azure Container Apps
      uses: azure/container-apps-deploy-action@v1
      with:
        resource-group: ${{ env.AZURE_RESOURCE_GROUP }}
        container-app-name: ${{ env.AZURE_CONTAINER_APP_NAME }}
        container-image: ${{ env.AZURE_CONTAINER_REGISTRY }}/asset-manager-web:${{ github.sha }}
```

### 8. Infrastructure as Code (Bicep)

#### Main Bicep Template
```bicep
@description('The location for all resources')
param location string = resourceGroup().location

@description('The name prefix for all resources')
param namePrefix string = 'asset-manager'

// Storage Account
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: '${namePrefix}storage${uniqueString(resourceGroup().id)}'
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
    allowBlobPublicAccess: true
  }
}

// Blob Container
resource blobContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: '${storageAccount.name}/default/images'
  properties: {
    publicAccess: 'Blob'
  }
}

// PostgreSQL Flexible Server
resource postgreSQLServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-03-01-preview' = {
  name: '${namePrefix}-postgres-${uniqueString(resourceGroup().id)}'
  location: location
  sku: {
    name: 'Standard_B2s'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: 'adminuser'
    administratorLoginPassword: 'P@ssw0rd123!'
    version: '13'
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
  }
}

// Service Bus Namespace
resource serviceBusNamespace 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: '${namePrefix}-servicebus-${uniqueString(resourceGroup().id)}'
  location: location
  sku: {
    name: 'Standard'
    tier: 'Standard'
  }
}

// Service Bus Queue
resource serviceBusQueue 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = {
  parent: serviceBusNamespace
  name: 'image-processing'
  properties: {
    maxSizeInMegabytes: 1024
    defaultMessageTimeToLive: 'P14D'
    duplicateDetectionHistoryTimeWindow: 'PT10M'
    requiresDuplicateDetection: true
    enablePartitioning: false
  }
}

// Key Vault
resource keyVault 'Microsoft.KeyVault/vaults@2023-02-01' = {
  name: '${namePrefix}-kv-${uniqueString(resourceGroup().id)}'
  location: location
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: tenant().tenantId
    accessPolicies: []
    enabledForDeployment: false
    enabledForDiskEncryption: false
    enabledForTemplateDeployment: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    enablePurgeProtection: false
  }
}

// Container Apps Environment
resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: '${namePrefix}-container-env'
  location: location
  properties: {
    zoneRedundant: false
  }
}

// Container App for Web Service
resource webContainerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: '${namePrefix}-web'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8080
        allowInsecure: false
      }
      secrets: [
        {
          name: 'storage-key'
          value: storageAccount.listKeys().keys[0].value
        }
        {
          name: 'servicebus-connection'
          value: listKeys('${serviceBusNamespace.id}/AuthorizationRules/RootManageSharedAccessKey', serviceBusNamespace.apiVersion).primaryConnectionString
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'asset-manager-web'
          image: 'nginx:latest' // Placeholder - will be updated by CI/CD
          env: [
            {
              name: 'AZURE_STORAGE_ACCOUNT_NAME'
              value: storageAccount.name
            }
            {
              name: 'AZURE_STORAGE_ACCOUNT_KEY'
              secretRef: 'storage-key'
            }
            {
              name: 'AZURE_SERVICEBUS_CONNECTION_STRING'
              secretRef: 'servicebus-connection'
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1.0Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 5
      }
    }
  }
}

// Container App for Worker Service
resource workerContainerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: '${namePrefix}-worker'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      secrets: [
        {
          name: 'storage-key'
          value: storageAccount.listKeys().keys[0].value
        }
        {
          name: 'servicebus-connection'
          value: listKeys('${serviceBusNamespace.id}/AuthorizationRules/RootManageSharedAccessKey', serviceBusNamespace.apiVersion).primaryConnectionString
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'asset-manager-worker'
          image: 'nginx:latest' // Placeholder - will be updated by CI/CD
          env: [
            {
              name: 'AZURE_STORAGE_ACCOUNT_NAME'
              value: storageAccount.name
            }
            {
              name: 'AZURE_STORAGE_ACCOUNT_KEY'
              secretRef: 'storage-key'
            }
            {
              name: 'AZURE_SERVICEBUS_CONNECTION_STRING'
              secretRef: 'servicebus-connection'
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1.0Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
}

// Outputs
output storageAccountName string = storageAccount.name
output postgreSQLServerName string = postgreSQLServer.name
output serviceBusNamespace string = serviceBusNamespace.name
output keyVaultName string = keyVault.name
output webAppUrl string = 'https://${webContainerApp.properties.configuration.ingress.fqdn}'
```

This technical implementation guide provides concrete code examples and configurations needed to execute the Azure migration plan effectively.