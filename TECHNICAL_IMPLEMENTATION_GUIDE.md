# Azure Migration - Technical Implementation Guide

## Service Migration Implementation Details

### 1. AWS S3 to Azure Blob Storage Migration

#### Maven Dependencies Update

**Remove AWS Dependencies:**
```xml
<!-- Remove these AWS dependencies -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>${aws-sdk.version}</version>
</dependency>
```

**Add Azure Dependencies:**
```xml
<!-- Add these Azure dependencies -->
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

#### Configuration Class Migration

**Current AWS S3 Configuration:**
```java
@Configuration
public class AwsS3Config {
    
    @Value("${aws.accessKey}")
    private String accessKey;
    
    @Value("${aws.secretKey}")
    private String secretKey;
    
    @Value("${aws.region}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
    }
}
```

**New Azure Blob Configuration:**
```java
@Configuration
public class AzureBlobConfig {
    
    @Value("${azure.storage.account-name}")
    private String accountName;
    
    @Value("${azure.storage.container-name}")
    private String containerName;
    
    @Bean
    public BlobServiceClient blobServiceClient() {
        String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
        return new BlobServiceClientBuilder()
            .endpoint(endpoint)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    }
    
    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        return blobServiceClient.getBlobContainerClient(containerName);
    }
}
```

#### Service Class Migration

**Current AWS S3 Service:**
```java
@Service
public class AwsS3Service implements StorageService {
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public AwsS3Service(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }
    
    @Override
    public void uploadFile(String key, InputStream inputStream, long contentLength) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentLength(contentLength)
                    .build(),
                RequestBody.fromInputStream(inputStream, contentLength)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }
    
    @Override
    public InputStream downloadFile(String key) {
        try {
            return s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
    
    @Override
    public void deleteFile(String key) {
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }
    
    @Override
    public boolean fileExists(String key) {
        try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check file existence in S3", e);
        }
    }
}
```

**New Azure Blob Service:**
```java
@Service
public class AzureBlobService implements StorageService {
    
    private final BlobContainerClient blobContainerClient;
    
    public AzureBlobService(BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
    }
    
    @Override
    public void uploadFile(String blobName, InputStream inputStream, long contentLength) {
        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            blobClient.upload(inputStream, contentLength, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Azure Blob Storage", e);
        }
    }
    
    @Override
    public InputStream downloadFile(String blobName) {
        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            return blobClient.downloadContent().toStream();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from Azure Blob Storage", e);
        }
    }
    
    @Override
    public void deleteFile(String blobName) {
        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from Azure Blob Storage", e);
        }
    }
    
    @Override
    public boolean fileExists(String blobName) {
        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            return blobClient.exists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to check file existence in Azure Blob Storage", e);
        }
    }
    
    @Override
    public String generatePresignedUrl(String blobName, Duration duration) {
        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plus(duration), 
                BlobSasPermission.parse("r")
            );
            return blobClient.getBlobUrl() + "?" + blobClient.generateSas(sasValues);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
```

### 2. RabbitMQ to Azure Service Bus Migration

#### Maven Dependencies Update

**Remove RabbitMQ Dependencies:**
```xml
<!-- Remove RabbitMQ dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Add Azure Service Bus Dependencies:**
```xml
<!-- Add Azure Service Bus dependencies -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-dependencies</artifactId>
    <version>5.22.0</version>
    <scope>import</scope>
    <type>pom</type>
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

#### Configuration Migration

**Current RabbitMQ Configuration:**
```java
@Configuration
@EnableRabbit
public class RabbitConfig {
    
    @Bean
    public TopicExchange imageProcessingExchange() {
        return new TopicExchange("image.processing.exchange");
    }
    
    @Bean
    public Queue imageProcessingQueue() {
        return QueueBuilder.durable("image.processing.queue").build();
    }
    
    @Bean
    public Binding imageProcessingBinding() {
        return BindingBuilder
            .bind(imageProcessingQueue())
            .to(imageProcessingExchange())
            .with("image.process.*");
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
```

**New Azure Service Bus Configuration:**
```java
@Configuration
@EnableAzureMessaging
public class ServiceBusConfig {
    
    @Bean
    public ServiceBusAdministrationClient serviceBusAdminClient(
        AzureServiceBusProperties properties, 
        TokenCredential credential) {
        return new ServiceBusAdministrationClientBuilder()
            .credential(properties.getFullyQualifiedNamespace(), credential)
            .buildClient();
    }
    
    @Bean
    public TopicProperties imageProcessingTopic(
        ServiceBusAdministrationClient adminClient) {
        String topicName = "image-processing-topic";
        try {
            return adminClient.getTopic(topicName);
        } catch (ResourceNotFoundException e) {
            return adminClient.createTopic(topicName);
        }
    }
    
    @Bean
    @DependsOn("imageProcessingTopic")
    public SubscriptionProperties imageProcessingSubscription(
        ServiceBusAdministrationClient adminClient) {
        String topicName = "image-processing-topic";
        String subscriptionName = "worker-subscription";
        try {
            return adminClient.getSubscription(topicName, subscriptionName);
        } catch (ResourceNotFoundException e) {
            return adminClient.createSubscription(topicName, subscriptionName);
        }
    }
}
```

#### Message Producer Migration

**Current RabbitMQ Producer:**
```java
@Service
public class ImageProcessingMessageProducer {
    
    private final RabbitTemplate rabbitTemplate;
    
    public ImageProcessingMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void sendImageProcessingMessage(ImageProcessingMessage message) {
        rabbitTemplate.convertAndSend(
            "image.processing.exchange", 
            "image.process.new", 
            message
        );
    }
}
```

**New Azure Service Bus Producer:**
```java
@Service
public class ImageProcessingMessageProducer {
    
    private final ServiceBusTemplate serviceBusTemplate;
    
    public ImageProcessingMessageProducer(ServiceBusTemplate serviceBusTemplate) {
        this.serviceBusTemplate = serviceBusTemplate;
    }
    
    public void sendImageProcessingMessage(ImageProcessingMessage message) {
        Message<ImageProcessingMessage> serviceBusMessage = MessageBuilder
            .withPayload(message)
            .build();
        serviceBusTemplate.send("image-processing-topic", serviceBusMessage);
    }
}
```

#### Message Consumer Migration

**Current RabbitMQ Consumer:**
```java
@Component
public class ImageProcessingMessageConsumer {
    
    private final FileProcessor fileProcessor;
    
    public ImageProcessingMessageConsumer(FileProcessor fileProcessor) {
        this.fileProcessor = fileProcessor;
    }
    
    @RabbitListener(queues = "image.processing.queue")
    public void handleImageProcessingMessage(ImageProcessingMessage message) {
        try {
            fileProcessor.processImage(message);
        } catch (Exception e) {
            // Handle error
            throw new RuntimeException("Failed to process image", e);
        }
    }
}
```

**New Azure Service Bus Consumer:**
```java
@Component
public class ImageProcessingMessageConsumer {
    
    private final FileProcessor fileProcessor;
    
    public ImageProcessingMessageConsumer(FileProcessor fileProcessor) {
        this.fileProcessor = fileProcessor;
    }
    
    @ServiceBusListener(destination = "image-processing-topic", group = "worker-subscription")
    public void handleImageProcessingMessage(
        ImageProcessingMessage message,
        @Header(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT) ServiceBusReceivedMessageContext context) {
        try {
            fileProcessor.processImage(message);
            context.complete();
        } catch (Exception e) {
            context.abandon();
            throw new RuntimeException("Failed to process image", e);
        }
    }
}
```

### 3. Application Properties Migration

#### Current Properties (application.properties)

**Web Module:**
```properties
# Application
spring.application.name=assets-manager

# AWS S3 Configuration
aws.accessKey=your-access-key
aws.secretKey=your-secret-key
aws.region=us-east-1
aws.s3.bucket=your-bucket-name

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/assets_manager
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# RabbitMQ Configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

**Worker Module:**
```properties
# Application
spring.application.name=assets-manager-worker
server.port=8081

# AWS S3 Configuration
aws.accessKeyId=your-access-key-Id
aws.secretKey=your-secret-key
aws.region=us-east-1
aws.s3.bucket=your-bucket-name

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/assets_manager
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# RabbitMQ Configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

#### New Azure Properties

**Web Module (application.properties):**
```properties
# Application
spring.application.name=assets-manager

# Azure Storage Configuration
azure.storage.account-name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container-name=assets-container

# Azure Database Configuration
spring.datasource.url=jdbc:postgresql://${AZURE_DB_SERVER}.postgres.database.azure.com:5432/assets_manager?sslmode=require
spring.datasource.username=${AZURE_DB_USERNAME}
spring.datasource.password=${AZURE_DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.entity-type=topic
spring.cloud.azure.servicebus.namespace=${SERVICE_BUS_NAMESPACE}

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Azure Application Insights
azure.application-insights.instrumentation-key=${APPINSIGHTS_INSTRUMENTATION_KEY}
```

**Worker Module (application.properties):**
```properties
# Application
spring.application.name=assets-manager-worker
server.port=8081

# Azure Storage Configuration
azure.storage.account-name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container-name=assets-container

# Azure Database Configuration
spring.datasource.url=jdbc:postgresql://${AZURE_DB_SERVER}.postgres.database.azure.com:5432/assets_manager?sslmode=require
spring.datasource.username=${AZURE_DB_USERNAME}
spring.datasource.password=${AZURE_DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Azure Service Bus Configuration
spring.cloud.azure.credential.managed-identity-enabled=true
spring.cloud.azure.credential.client-id=${AZURE_CLIENT_ID}
spring.cloud.azure.servicebus.entity-type=topic
spring.cloud.azure.servicebus.namespace=${SERVICE_BUS_NAMESPACE}

# Azure Application Insights
azure.application-insights.instrumentation-key=${APPINSIGHTS_INSTRUMENTATION_KEY}
```

### 4. Controller Updates

#### Updated S3Controller to BlobController

**Current S3Controller:**
```java
@Controller
@RequestMapping("/s3")
public class S3Controller {
    
    private final AwsS3Service s3Service;
    
    public S3Controller(AwsS3Service s3Service) {
        this.s3Service = s3Service;
    }
    
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String key = generateFileKey(file.getOriginalFilename());
            s3Service.uploadFile(key, file.getInputStream(), file.getSize());
            return ResponseEntity.ok("File uploaded successfully: " + key);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Upload failed: " + e.getMessage());
        }
    }
    
    @GetMapping("/download/{key}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String key) {
        try {
            InputStream inputStream = s3Service.downloadFile(key);
            InputStreamResource resource = new InputStreamResource(inputStream);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
```

**New BlobController:**
```java
@Controller
@RequestMapping("/storage")
public class BlobController {
    
    private final AzureBlobService blobService;
    
    public BlobController(AzureBlobService blobService) {
        this.blobService = blobService;
    }
    
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String blobName = generateBlobName(file.getOriginalFilename());
            blobService.uploadFile(blobName, file.getInputStream(), file.getSize());
            return ResponseEntity.ok("File uploaded successfully: " + blobName);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Upload failed: " + e.getMessage());
        }
    }
    
    @GetMapping("/download/{blobName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String blobName) {
        try {
            InputStream inputStream = blobService.downloadFile(blobName);
            InputStreamResource resource = new InputStreamResource(inputStream);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + blobName + "\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @GetMapping("/presigned-url/{blobName}")
    public ResponseEntity<String> getPresignedUrl(@PathVariable String blobName) {
        try {
            String url = blobService.generatePresignedUrl(blobName, Duration.ofHours(1));
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to generate presigned URL: " + e.getMessage());
        }
    }
    
    private String generateBlobName(String originalFilename) {
        return UUID.randomUUID().toString() + "_" + originalFilename;
    }
}
```

### 5. Testing Updates

#### Unit Test Updates

**Azure Blob Service Test:**
```java
@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {
    
    @Mock
    private BlobContainerClient blobContainerClient;
    
    @Mock
    private BlobClient blobClient;
    
    @InjectMocks
    private AzureBlobService azureBlobService;
    
    @Test
    void uploadFile_Success() throws IOException {
        // Given
        String blobName = "test-blob";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        long contentLength = 12L;
        
        when(blobContainerClient.getBlobClient(blobName)).thenReturn(blobClient);
        
        // When
        azureBlobService.uploadFile(blobName, inputStream, contentLength);
        
        // Then
        verify(blobClient).upload(inputStream, contentLength, true);
    }
    
    @Test
    void downloadFile_Success() {
        // Given
        String blobName = "test-blob";
        BinaryData mockData = BinaryData.fromString("test content");
        
        when(blobContainerClient.getBlobClient(blobName)).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(mockData);
        
        // When
        InputStream result = azureBlobService.downloadFile(blobName);
        
        // Then
        assertThat(result).isNotNull();
        verify(blobClient).downloadContent();
    }
    
    @Test
    void fileExists_True() {
        // Given
        String blobName = "test-blob";
        
        when(blobContainerClient.getBlobClient(blobName)).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        
        // When
        boolean exists = azureBlobService.fileExists(blobName);
        
        // Then
        assertThat(exists).isTrue();
    }
}
```

#### Integration Test Updates

**Azure Service Bus Integration Test:**
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.azure.servicebus.namespace=test-namespace",
    "spring.cloud.azure.credential.managed-identity-enabled=false"
})
class ServiceBusIntegrationTest {
    
    @Autowired
    private ImageProcessingMessageProducer messageProducer;
    
    @MockBean
    private ServiceBusTemplate serviceBusTemplate;
    
    @Test
    void sendMessage_Success() {
        // Given
        ImageProcessingMessage message = new ImageProcessingMessage();
        message.setImageId("test-image-id");
        message.setOperation("RESIZE");
        
        // When
        messageProducer.sendImageProcessingMessage(message);
        
        // Then
        verify(serviceBusTemplate).send(eq("image-processing-topic"), any(Message.class));
    }
}
```

### 6. Environment Configuration

#### Azure App Service Configuration

**Web App Configuration:**
```json
{
  "name": "assets-manager-web",
  "location": "East US",
  "sku": {
    "name": "P1V3",
    "tier": "Premium"
  },
  "properties": {
    "serverFarmId": "/subscriptions/{subscription-id}/resourceGroups/{rg}/providers/Microsoft.Web/serverfarms/{app-service-plan}",
    "siteConfig": {
      "appSettings": [
        {
          "name": "AZURE_STORAGE_ACCOUNT_NAME",
          "value": "assetsmanagerstorage"
        },
        {
          "name": "AZURE_DB_SERVER",
          "value": "assets-manager-db.postgres.database.azure.com"
        },
        {
          "name": "AZURE_DB_USERNAME",
          "value": "adminuser"
        },
        {
          "name": "SERVICE_BUS_NAMESPACE",
          "value": "assets-manager-servicebus.servicebus.windows.net"
        },
        {
          "name": "AZURE_CLIENT_ID",
          "value": "{managed-identity-client-id}"
        }
      ],
      "javaVersion": "11",
      "javaContainer": "JAVA",
      "javaContainerVersion": "11"
    }
  }
}
```

This technical implementation guide provides the specific code changes, configuration updates, and testing approaches needed to successfully migrate from AWS to Azure services. Each section includes detailed before-and-after examples to ensure a smooth transition.