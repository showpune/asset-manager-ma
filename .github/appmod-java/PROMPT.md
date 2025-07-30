# Modernization Planning Prompt: Spring Web Application to Azure App Service

## Modernization Planning Request

Create a detailed modernization plan for transforming this Spring Boot multi-module asset management application from **current AWS-based Spring Web Application** to **Azure App Service** deployment, focusing on identifying the required changes, their sequence, dependencies, and associated risks.

## Application Analysis

### Current Architecture
- **Multi-module Maven project** with parent POM and two modules:
  - `web` module: Spring Boot web application with Thymeleaf, Spring Web, JPA, PostgreSQL, RabbitMQ, and AWS S3 integration
  - `worker` module: Spring Boot worker application with RabbitMQ and AWS S3 for thumbnail processing
- **Technology Stack:**
  - Spring Boot 3.4.3
  - Java 11
  - PostgreSQL database
  - RabbitMQ for messaging between web and worker modules
  - AWS S3 for file storage and thumbnails
  - Thymeleaf for web templating
  - Maven for build management
- **Dependencies:**
  - AWS SDK 2.25.13 for S3 operations
  - Spring Boot Starter AMQP for RabbitMQ
  - Spring Boot Starter Data JPA for database access
  - PostgreSQL driver
  - Lombok for code generation

### Current Cloud Dependencies
- **AWS S3** for file storage (both web and worker modules)
- **RabbitMQ** for asynchronous message processing
- **PostgreSQL** database for metadata storage
- **Local file system** as alternative storage (dev profile)

## Scope

- ✅ Analysis of required code modifications to move from AWS-based Spring application to Azure App Service
- ✅ Identification of configuration changes needed for Azure services
- ✅ Dependency management planning (replacing AWS SDK with Azure SDK)
- ✅ Visual modernization sequence diagrams with Mermaid (AWS -> Azure paths)
- ✅ Risk assessment and mitigation strategies for multi-module deployment
- ✅ Technical roadmap with clear implementation phases
- ✅ Modernization success criteria and validation approach

## Success Criteria

1. Comprehensive modernization plan with clear implementation phases for both web and worker modules
2. Detailed AWS -> Azure modernization sequence with dependencies and ordering
3. Visual diagrams illustrating the modernization path from AWS to Azure services
4. Risk assessment with mitigation strategies for each phase
5. Success metrics and validation approach for the modernization
6. Proper handling of multi-module architecture in Azure App Service

## Execution Process

1. Analyze the codebase to identify all AWS components and their Azure equivalents
2. Create a `modernization-sequence-diagrams.md` file in .migration folder with:
   - Complete modernization sequence with dependencies
   - Specific AWS -> Azure modernization paths for each service component
   - Decision trees for key modernization choices
   - Critical path analysis for multi-module deployment
3. Define modernization phases systematically:
   - Foundation setup and Azure service preparation
   - AWS to Azure service migration
   - Multi-module deployment strategy
   - Configuration externalization
   - Testing and validation
4. For each phase, provide:
   - Detailed tasks and their dependencies
   - Technical implementation guidance
   - Validation and verification approach
   - Risk assessment and mitigation

## Modernization Planning Documents

Create a `modernization-sequence-diagrams.md` file with:
- [ ] AWS -> Azure modernization steps table showing the precise ordered sequence
- [ ] Complete modernization sequence with all steps using Mermaid diagrams
- [ ] Specific AWS -> Azure modernization paths for each technology component
- [ ] Dependencies between different components (web and worker modules)
- [ ] Critical path analysis for multi-module architecture
- [ ] Risk assessment visualization

### Key Modernization Areas

#### 1. Storage Layer Migration
**From:** AWS S3 (file storage, thumbnails)
**To:** Azure Blob Storage or Azure Files
- Replace AWS SDK S3Client with Azure Storage Blob SDK
- Update configuration from aws.s3.bucket to Azure storage account settings
- Modify file upload/download operations in both web and worker modules
- Update thumbnail storage and retrieval logic

#### 2. Message Queue Migration
**From:** RabbitMQ (localhost)
**To:** Azure Service Bus
- Replace Spring AMQP RabbitMQ with Azure Service Bus integration
- Update message processing in BackupMessageProcessor and worker module
- Migrate queue configurations and connection settings
- Ensure message serialization compatibility

#### 3. Database Migration
**From:** PostgreSQL (localhost)
**To:** Azure Database for PostgreSQL or Azure SQL Database
- Update connection strings and authentication
- Ensure JPA configurations work with Azure database services
- Plan for connection pooling and security considerations

#### 4. Application Deployment
**From:** Traditional JAR deployment
**To:** Azure App Service
- Configure multi-module deployment strategy
- Set up separate App Service instances for web and worker modules
- Configure environment variables and application settings
- Set up scaling and monitoring

#### 5. Configuration Management
**From:** application.properties files
**To:** Azure App Configuration or environment variables
- Externalize all configuration settings
- Implement secure storage for sensitive credentials
- Set up different configurations for dev/staging/production environments

### Modernization Sequencing

Create a detailed AWS -> Azure modernization steps table:

| Order | From (AWS/Current) | To (Azure) | Dependencies | Migration Type | Risk Level | Description |
|-------|-------------------|------------|--------------|------------|------------|-------------|
| 1 | Local Development | Azure Resource Group | None | Infrastructure Setup | Low | Create Azure resource group and basic infrastructure |
| 2 | PostgreSQL localhost | Azure Database for PostgreSQL | Step 1 | Infrastructure Setup | Medium | Provision managed PostgreSQL database in Azure |
| 3 | AWS S3 Configuration | Azure Blob Storage | Step 1 | Infrastructure Setup | Medium | Create Azure Storage Account and configure blob containers |
| 4 | RabbitMQ localhost | Azure Service Bus | Step 1 | Infrastructure Setup | Medium | Provision Azure Service Bus namespace and queues |
| 5 | AWS SDK Dependencies | Azure SDK Dependencies | None | Application Code Change | Medium | Update Maven dependencies in both modules |
| 6 | S3Service Implementation | Azure Blob Service | Step 5 | Application Code Change | High | Replace AWS S3 operations with Azure Blob Storage |
| 7 | RabbitMQ Configuration | Service Bus Configuration | Step 4,5 | Application Code Change | High | Update message queue implementation |
| 8 | Database Connection | Azure DB Connection | Step 2 | Configuration | Medium | Update connection strings and authentication |
| 9 | application.properties | Azure App Configuration | Step 1-4 | Configuration | Medium | Externalize configuration to Azure services |
| 10 | JAR Packaging | Azure App Service Ready | Step 6-9 | Deployment | High | Prepare applications for Azure App Service deployment |
| 11 | Web Module Deployment | Azure App Service Web | Step 10 | Deployment | High | Deploy web module to Azure App Service |
| 12 | Worker Module Deployment | Azure App Service Worker | Step 11 | Deployment | High | Deploy worker module to separate App Service instance |
| 13 | End-to-End Testing | Production Validation | Step 11-12 | Manual Action | High | Comprehensive testing of file upload, processing, and storage |

### Implementation Phases

#### Phase 1: Infrastructure Foundation (Low-Medium Risk)
- Create Azure Resource Group
- Provision Azure Database for PostgreSQL
- Set up Azure Blob Storage account
- Configure Azure Service Bus namespace
- Set up Azure App Configuration service

#### Phase 2: Application Dependencies Update (Medium Risk)
- Update Maven dependencies to remove AWS SDK
- Add Azure SDK dependencies for Blob Storage and Service Bus
- Update Spring Boot configuration for Azure services
- Configure Azure authentication (Managed Identity or connection strings)

#### Phase 3: Core Service Migration (High Risk)
- Replace AwsS3Service with Azure Blob Storage service
- Update RabbitMQ configuration to use Azure Service Bus
- Migrate message processing logic in worker module
- Update database connection configuration

#### Phase 4: Configuration Externalization (Medium Risk)
- Move configuration from application.properties to Azure App Configuration
- Set up environment-specific configurations
- Implement secure credential management
- Configure application settings for Azure App Service

#### Phase 5: Deployment and Testing (High Risk)
- Package applications for Azure App Service deployment
- Deploy web module to primary App Service instance
- Deploy worker module to secondary App Service instance
- Configure inter-service communication
- Perform end-to-end testing and validation

### Risk Assessment and Mitigation

#### High-Risk Areas:
1. **Multi-module deployment complexity**
   - Mitigation: Deploy modules separately, test communication thoroughly
2. **File storage migration data integrity**
   - Mitigation: Implement parallel processing, verify data consistency
3. **Message queue compatibility**
   - Mitigation: Thorough testing of message serialization/deserialization

#### Medium-Risk Areas:
1. **Database connection changes**
   - Mitigation: Use connection string testing, implement retry logic
2. **Configuration management**
   - Mitigation: Staged configuration migration, fallback mechanisms

## Azure-Specific Implementation Guidance

### Storage Migration
```java
// Current AWS S3 implementation
@Service
@RequiredArgsConstructor
@Profile("!dev")
public class AwsS3Service implements StorageService {
    private final S3Client s3Client;
    // ... existing implementation
}

// Target Azure Blob Storage implementation
@Service
@RequiredArgsConstructor
@Profile("azure")
public class AzureBlobService implements StorageService {
    private final BlobServiceClient blobServiceClient;
    // ... new implementation
}
```

### Message Queue Migration
```properties
# Current RabbitMQ configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Target Azure Service Bus configuration
azure.servicebus.connection-string=${AZURE_SERVICEBUS_CONNECTION_STRING}
azure.servicebus.queue-name=image-processing-queue
```

### Database Configuration
```properties
# Current PostgreSQL configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/assets_manager
spring.datasource.username=postgres
spring.datasource.password=postgres

# Target Azure Database configuration
spring.datasource.url=jdbc:postgresql://${AZURE_DB_SERVER}:5432/${AZURE_DB_NAME}?sslmode=require
spring.datasource.username=${AZURE_DB_USERNAME}
spring.datasource.password=${AZURE_DB_PASSWORD}
```

## Validation and Success Metrics

### Technical Validation
- [ ] Both web and worker modules deploy successfully to Azure App Service
- [ ] File upload and storage operations work with Azure Blob Storage
- [ ] Message processing between modules functions correctly via Azure Service Bus
- [ ] Database operations perform adequately with Azure Database for PostgreSQL
- [ ] All configuration is externalized and secure

### Performance Validation
- [ ] File upload/download performance meets or exceeds current benchmarks
- [ ] Message processing latency remains within acceptable thresholds
- [ ] Database query performance is maintained or improved
- [ ] Application startup time is optimized for Azure App Service

### Business Validation
- [ ] All existing functionality works as expected
- [ ] User experience remains consistent or improved
- [ ] System reliability and availability meet SLA requirements
- [ ] Cost optimization compared to current AWS infrastructure

Ensure the modernization plan comprehensively addresses all aspects of the transformation from AWS-based Spring Web Application to Azure App Service deployment, with particular attention to the multi-module architecture and service dependencies.