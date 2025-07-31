# Azure Migration Testing Strategy

## Overview

This document outlines the comprehensive testing strategy for migrating the Asset Manager application from AWS to Azure. The strategy ensures functional parity, performance standards, and reliability during and after the migration.

## Testing Objectives

1. **Functional Parity**: Ensure all current features work identically with Azure services
2. **Performance Standards**: Maintain or improve current performance metrics
3. **Data Integrity**: Verify complete and accurate data migration
4. **Security Compliance**: Validate security controls and access patterns
5. **Reliability**: Ensure system stability under various load conditions

## Testing Phases and Timeline

### Phase 1: Unit Testing (Week 1)
- Component-level testing
- Service layer validation
- Configuration testing

### Phase 2: Integration Testing (Week 2)
- Azure service integration
- Database connectivity
- Message queue functionality

### Phase 3: System Testing (Week 3)
- End-to-end workflows
- Performance testing
- Security testing

### Phase 4: User Acceptance Testing (Week 4)
- Business workflow validation
- User experience testing
- Production readiness assessment

## Detailed Testing Strategy

### 1. Unit Testing

#### 1.1 Azure Blob Storage Service Tests
```java
@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {

    @Mock
    private BlobServiceClient blobServiceClient;
    
    @Mock
    private BlobContainerClient containerClient;
    
    @Mock
    private BlobClient blobClient;

    @InjectMocks
    private AzureBlobService azureBlobService;

    @Test
    @DisplayName("Should upload file successfully to Azure Blob Storage")
    void testUploadFile() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "test.jpg", 
            "test.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );
        
        when(blobServiceClient.getBlobContainerClient(anyString()))
            .thenReturn(containerClient);
        when(containerClient.getBlobClient(anyString()))
            .thenReturn(blobClient);

        // When
        azureBlobService.uploadObject(file);

        // Then
        verify(blobClient).upload(any(InputStream.class), eq(file.getSize()), eq(true));
        verify(blobClient).setHttpHeaders(any(BlobHttpHeaders.class));
    }

    @Test
    @DisplayName("Should handle upload failures gracefully")
    void testUploadFailure() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
            "test.jpg", 
            "test.jpg", 
            "image/jpeg", 
            "test content".getBytes()
        );
        
        when(blobServiceClient.getBlobContainerClient(anyString()))
            .thenThrow(new BlobStorageException("Storage error", 
                new HttpResponse(500), null));

        // When & Then
        assertThrows(IOException.class, () -> azureBlobService.uploadObject(file));
    }

    @Test
    @DisplayName("Should download file successfully")
    void testDownloadFile() throws IOException {
        // Given
        String blobName = "test-blob.jpg";
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        when(blobServiceClient.getBlobContainerClient(anyString()))
            .thenReturn(containerClient);
        when(containerClient.getBlobClient(blobName))
            .thenReturn(blobClient);
        when(blobClient.openInputStream())
            .thenReturn(mockInputStream);

        // When
        InputStream result = azureBlobService.getObject(blobName);

        // Then
        assertThat(result).isNotNull();
        verify(blobClient).openInputStream();
    }

    @Test
    @DisplayName("Should list files with correct metadata")
    void testListFiles() {
        // Given
        BlobItem blobItem = mock(BlobItem.class);
        BlobItemProperties properties = mock(BlobItemProperties.class);
        
        when(blobItem.getName()).thenReturn("test-file.jpg");
        when(blobItem.getProperties()).thenReturn(properties);
        when(properties.getContentLength()).thenReturn(1024L);
        when(properties.getLastModified()).thenReturn(OffsetDateTime.now());
        
        when(blobServiceClient.getBlobContainerClient(anyString()))
            .thenReturn(containerClient);
        when(containerClient.listBlobs())
            .thenReturn(Arrays.asList(blobItem));

        // When
        List<S3StorageItem> result = azureBlobService.listObjects();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFilename()).isEqualTo("test-file.jpg");
        assertThat(result.get(0).getSize()).isEqualTo(1024L);
    }
}
```

#### 1.2 Configuration Testing
```java
@SpringBootTest
@TestPropertySource(properties = {
    "azure.storage.connection-string=DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net",
    "azure.storage.container-name=test-container"
})
class AzureConfigurationTest {

    @Autowired
    private BlobServiceClient blobServiceClient;

    @Test
    @DisplayName("Should create blob service client with correct configuration")
    void testBlobServiceClientConfiguration() {
        assertThat(blobServiceClient).isNotNull();
        assertThat(blobServiceClient.getAccountName()).isEqualTo("test");
    }
}
```

### 2. Integration Testing

#### 2.1 Azure Storage Integration Tests
```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-integration-test.properties")
@Testcontainers
class AzureStorageIntegrationTest {

    @Container
    static AzuriteContainer azurite = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:latest")
            .withExposedPorts(10000, 10001, 10002);

    @Autowired
    private AzureBlobService azureBlobService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.storage.connection-string", 
            () -> azurite.getConnectionString());
    }

    @Test
    @DisplayName("Should perform complete upload-download-delete cycle")
    void testCompleteFileCycle() throws IOException {
        // Given
        MockMultipartFile testFile = new MockMultipartFile(
            "integration-test.jpg",
            "integration-test.jpg",
            "image/jpeg",
            "Integration test content".getBytes()
        );

        // When - Upload
        azureBlobService.uploadObject(testFile);

        // Then - Verify upload
        List<S3StorageItem> files = azureBlobService.listObjects();
        assertThat(files).hasSize(1);
        
        S3StorageItem uploadedFile = files.get(0);
        assertThat(uploadedFile.getFilename()).contains("integration-test.jpg");

        // When - Download
        InputStream downloadedContent = azureBlobService.getObject(uploadedFile.getKey());

        // Then - Verify download
        String content = new String(downloadedContent.readAllBytes());
        assertThat(content).isEqualTo("Integration test content");

        // When - Delete
        azureBlobService.deleteObject(uploadedFile.getKey());

        // Then - Verify deletion
        List<S3StorageItem> remainingFiles = azureBlobService.listObjects();
        assertThat(remainingFiles).isEmpty();
    }

    @Test
    @DisplayName("Should handle concurrent uploads correctly")
    void testConcurrentUploads() throws InterruptedException {
        // Given
        int numberOfThreads = 5;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int fileIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    MockMultipartFile file = new MockMultipartFile(
                        "concurrent-test-" + fileIndex + ".jpg",
                        "concurrent-test-" + fileIndex + ".jpg",
                        "image/jpeg",
                        ("Content " + fileIndex).getBytes()
                    );
                    azureBlobService.uploadObject(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all uploads to complete
        latch.await(30, TimeUnit.SECONDS);

        // Then
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        List<S3StorageItem> files = azureBlobService.listObjects();
        assertThat(files).hasSize(numberOfThreads);
    }
}
```

#### 2.2 Database Integration Tests
```java
@SpringBootTest
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ImageMetadataRepositoryIntegrationTest {

    @Autowired
    private ImageMetadataRepository repository;

    @Test
    @DisplayName("Should save and retrieve image metadata")
    void testSaveAndRetrieveMetadata() {
        // Given
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename("test-image.jpg");
        metadata.setContentType("image/jpeg");
        metadata.setSize(1024L);
        metadata.setS3Key("azure-blob-key");
        metadata.setS3Url("https://storage.blob.core.windows.net/container/blob");

        // When
        ImageMetadata saved = repository.save(metadata);

        // Then
        assertThat(saved.getId()).isNotNull();
        
        Optional<ImageMetadata> retrieved = repository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getFilename()).isEqualTo("test-image.jpg");
    }
}
```

### 3. Performance Testing

#### 3.1 Load Testing Configuration
```yaml
# jmeter-load-test.jmx equivalent in YAML
loadTest:
  threadGroups:
    - name: "FileUpload"
      threads: 10
      rampUp: 30
      duration: 300
      requests:
        - name: "Upload Image"
          method: POST
          url: "/s3/upload"
          body: "multipart/form-data"
          assertions:
            - responseTime: < 5000ms
            - responseCode: 200
            
    - name: "FileDownload"
      threads: 20
      rampUp: 30
      duration: 300
      requests:
        - name: "Download Image"
          method: GET
          url: "/s3/view/${imageId}"
          assertions:
            - responseTime: < 2000ms
            - responseCode: 200
```

#### 3.2 Performance Test Implementation
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PerformanceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should handle multiple concurrent uploads within acceptable time")
    void testConcurrentUploadPerformance() throws InterruptedException {
        // Given
        int numberOfRequests = 50;
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());

        // When
        for (int i = 0; i < numberOfRequests; i++) {
            CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    // Simulate file upload
                    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
                    parts.add("file", createTestFile());
                    
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        "/s3/upload", parts, String.class);
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    responseTimes.add(responseTime);
                    
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);

        // Then
        assertThat(responseTimes).hasSize(numberOfRequests);
        
        double averageResponseTime = responseTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
            
        long maxResponseTime = responseTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0L);

        System.out.println("Average response time: " + averageResponseTime + "ms");
        System.out.println("Max response time: " + maxResponseTime + "ms");

        // Performance assertions
        assertThat(averageResponseTime).isLessThan(3000.0); // 3 seconds average
        assertThat(maxResponseTime).isLessThan(10000L); // 10 seconds max
    }

    private Resource createTestFile() {
        return new ByteArrayResource("Test file content".getBytes()) {
            @Override
            public String getFilename() {
                return "test-file.jpg";
            }
        };
    }
}
```

### 4. Security Testing

#### 4.1 Authentication and Authorization Tests
```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should require authentication for file upload")
    void testUploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/s3/upload")
                .file("file", "test content".getBytes()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should validate file types")
    void testFileTypeValidation() throws Exception {
        MockMultipartFile maliciousFile = new MockMultipartFile(
            "file", 
            "script.exe", 
            "application/exe", 
            "malicious content".getBytes()
        );

        mockMvc.perform(multipart("/s3/upload")
                .file(maliciousFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should enforce file size limits")
    void testFileSizeLimit() throws Exception {
        byte[] largeFile = new byte[11 * 1024 * 1024]; // 11MB file
        Arrays.fill(largeFile, (byte) 'A');

        MockMultipartFile oversizedFile = new MockMultipartFile(
            "file", 
            "large.jpg", 
            "image/jpeg", 
            largeFile
        );

        mockMvc.perform(multipart("/s3/upload")
                .file(oversizedFile))
                .andExpect(status().isBadRequest());
    }
}
```

### 5. End-to-End Testing

#### 5.1 Complete Workflow Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class EndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ImageMetadataRepository repository;

    private static String uploadedFileKey;

    @Test
    @Order(1)
    @DisplayName("Should complete full upload workflow")
    void testUploadWorkflow() {
        // Given
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", createTestImageFile());

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/s3/upload", parts, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).contains("/s3");

        // Verify database entry
        List<ImageMetadata> metadata = repository.findAll();
        assertThat(metadata).hasSize(1);
        uploadedFileKey = metadata.get(0).getS3Key();
    }

    @Test
    @Order(2)
    @DisplayName("Should display uploaded file in list")
    void testFileListDisplay() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity("/s3", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("test-image.jpg");
    }

    @Test
    @Order(3)
    @DisplayName("Should download uploaded file")
    void testFileDownload() {
        // When
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
            "/s3/view/" + uploadedFileKey, byte[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("image");
    }

    @Test
    @Order(4)
    @DisplayName("Should delete uploaded file")
    void testFileDeletion() {
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/s3/delete/" + uploadedFileKey,
            HttpMethod.DELETE,
            null,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // Verify database cleanup
        List<ImageMetadata> metadata = repository.findAll();
        assertThat(metadata).isEmpty();
    }

    private Resource createTestImageFile() {
        return new ByteArrayResource("Test image content".getBytes()) {
            @Override
            public String getFilename() {
                return "test-image.jpg";
            }
        };
    }
}
```

## Test Data Management

### 1. Test Data Sets
```yaml
testData:
  smallImage:
    size: 1KB
    type: "image/jpeg"
    name: "small-test.jpg"
    
  mediumImage:
    size: 1MB
    type: "image/png"
    name: "medium-test.png"
    
  largeImage:
    size: 5MB
    type: "image/jpeg"
    name: "large-test.jpg"
    
  invalidFile:
    size: 100B
    type: "application/exe"
    name: "malicious.exe"
```

### 2. Environment Configuration
```properties
# application-test.properties
azure.storage.connection-string=UseDevelopmentStorage=true
azure.storage.container-name=test-container

spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop

# Disable actual message sending during tests
spring.rabbitmq.template.routing-key=test-queue
```

## Continuous Testing Strategy

### 1. CI/CD Pipeline Integration
```yaml
# .github/workflows/azure-migration-tests.yml
name: Azure Migration Tests

on:
  push:
    branches: [ feature/azure-migration ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      azurite:
        image: mcr.microsoft.com/azure-storage/azurite:latest
        ports:
          - 10000:10000
          - 10001:10001
          - 10002:10002
          
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
        
    - name: Run Unit Tests
      run: ./mvnw test
      
    - name: Run Integration Tests
      run: ./mvnw verify -Pintegration-tests
      
    - name: Run Performance Tests
      run: ./mvnw verify -Pperformance-tests
      
    - name: Generate Test Report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Azure Migration Tests
        path: '**/target/surefire-reports/*.xml'
        reporter: java-junit
```

### 2. Test Metrics and Reporting
```java
@Component
public class TestMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public TestMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordTestExecution(String testName, Duration duration, boolean success) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("test.execution.time")
            .tag("test.name", testName)
            .tag("result", success ? "success" : "failure")
            .register(meterRegistry));
    }
}
```

## Success Criteria and Validation

### Functional Testing Success Criteria
- [ ] 100% of unit tests pass
- [ ] All integration tests pass with real Azure services
- [ ] End-to-end workflows complete successfully
- [ ] No regression in existing functionality

### Performance Testing Success Criteria
- [ ] Upload response time < 5 seconds (95th percentile)
- [ ] Download response time < 2 seconds (95th percentile)
- [ ] System handles 50 concurrent users
- [ ] Memory usage remains stable under load

### Security Testing Success Criteria
- [ ] All security controls function correctly
- [ ] File type validation prevents malicious uploads
- [ ] Authentication and authorization work as expected
- [ ] No sensitive data exposed in logs or responses

### Reliability Testing Success Criteria
- [ ] System recovers gracefully from failures
- [ ] Data consistency maintained during operations
- [ ] No data loss during error conditions
- [ ] Monitoring and alerting function correctly

This comprehensive testing strategy ensures a successful and reliable migration to Azure while maintaining the highest standards of quality and performance.