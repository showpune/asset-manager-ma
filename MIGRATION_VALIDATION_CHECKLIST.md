# Azure Migration Validation Checklist

## Pre-Migration Validation

### Infrastructure Readiness

#### Azure Resource Group Setup
- [ ] Resource Group created: `assets-manager-rg`
- [ ] Location selected: `East US` (or appropriate region)
- [ ] Tagging strategy implemented
- [ ] Resource naming convention defined
- [ ] RBAC permissions configured

#### Azure Database for PostgreSQL
- [ ] Azure Database for PostgreSQL server created
- [ ] Server name: `assets-manager-db`
- [ ] SKU: General Purpose, 2 vCores, 100GB storage
- [ ] SSL enforcement enabled
- [ ] Firewall rules configured
- [ ] Database `assets_manager` created
- [ ] Connection string tested from development environment
- [ ] Backup retention configured (7 days minimum)

#### Azure Storage Account
- [ ] Storage account created: `assetsmanagerstorage`
- [ ] Account type: StorageV2 (general purpose v2)
- [ ] Replication: LRS (Locally Redundant Storage)
- [ ] Access tier: Hot
- [ ] Container created: `assets-container`
- [ ] CORS settings configured for web access
- [ ] Access keys and connection string available

#### Azure Service Bus
- [ ] Service Bus namespace created: `assets-manager-servicebus`
- [ ] SKU: Standard tier
- [ ] Topic created: `image-processing-topic`
- [ ] Subscription created: `worker-subscription`
- [ ] Access policies configured
- [ ] Connection string available

#### Azure Key Vault
- [ ] Key Vault created: `assets-manager-keyvault`
- [ ] SKU: Standard
- [ ] Access policies configured for application
- [ ] Secrets created for database password
- [ ] Storage account keys stored
- [ ] Service Bus connection strings stored

#### Azure App Service
- [ ] App Service Plan created: `assets-manager-plan`
- [ ] SKU: Premium v3 P1V3
- [ ] Web App created: `assets-manager-web`
- [ ] Worker App created: `assets-manager-worker`
- [ ] Managed Identity enabled for both apps
- [ ] Application settings configured
- [ ] Java 11 runtime configured

#### Azure Application Insights
- [ ] Application Insights resource created
- [ ] Instrumentation key available
- [ ] Connected to App Service applications
- [ ] Custom metrics and logging configured

### Security Configuration

#### Managed Identity
- [ ] System-assigned managed identity enabled for Web App
- [ ] System-assigned managed identity enabled for Worker App
- [ ] Identity assigned appropriate roles:
  - [ ] Storage Blob Data Contributor
  - [ ] Azure Service Bus Data Owner
  - [ ] Key Vault Secrets User

#### Network Security
- [ ] Virtual Network created (if using VNet integration)
- [ ] Network Security Groups configured
- [ ] Private endpoints configured (if required)
- [ ] SSL certificates installed
- [ ] HTTPS redirect enabled

#### Access Control
- [ ] RBAC roles assigned to development team
- [ ] Service principal created for CI/CD (if needed)
- [ ] Appropriate permissions for deployment
- [ ] Security policies reviewed and approved

---

## Migration Phase Validation

### Phase 1: Database Migration

#### Pre-Migration Database Validation
- [ ] Source database schema documented
- [ ] Data volume assessment completed
- [ ] Current database backup created
- [ ] Migration script prepared
- [ ] Test database migration completed successfully

#### Database Migration Execution
- [ ] Production data exported from source PostgreSQL
- [ ] Data imported to Azure Database for PostgreSQL
- [ ] Schema migration completed
- [ ] Indexes recreated
- [ ] Constraints validated
- [ ] Foreign key relationships verified

#### Post-Migration Database Validation
- [ ] Row count validation between source and target
- [ ] Data integrity checks passed
- [ ] Sample data queries executed successfully
- [ ] Performance baseline established
- [ ] Connection from applications tested
- [ ] Backup and restore procedures tested

**Validation Queries:**
```sql
-- Row count validation
SELECT 'image_metadata' as table_name, COUNT(*) as row_count FROM image_metadata
UNION ALL
SELECT 'other_table' as table_name, COUNT(*) as row_count FROM other_table;

-- Data integrity spot checks
SELECT * FROM image_metadata LIMIT 10;
SELECT MAX(created_date), MIN(created_date) FROM image_metadata;
```

### Phase 2: Storage Migration

#### Pre-Migration Storage Validation
- [ ] Current S3 bucket contents inventoried
- [ ] File access patterns documented
- [ ] Storage usage metrics captured
- [ ] Migration strategy for large files defined

#### Storage Migration Execution
- [ ] Azure Blob Storage container created
- [ ] File migration tool tested (Azure Data Factory or custom script)
- [ ] Batch file migration executed
- [ ] File integrity validation performed

#### Post-Migration Storage Validation
- [ ] File count matches between S3 and Blob Storage
- [ ] Random file downloads tested
- [ ] File metadata preserved
- [ ] Access permissions verified
- [ ] CDN configuration updated (if applicable)

**Validation Scripts:**
```bash
# Azure CLI validation commands
az storage blob list --account-name assetsmanagerstorage --container-name assets-container --output table
az storage blob show --account-name assetsmanagerstorage --container-name assets-container --name sample-file.jpg
```

### Phase 3: Application Code Migration

#### Code Repository Updates
- [ ] New feature branch created: `azure-migration`
- [ ] Maven dependencies updated (AWS → Azure)
- [ ] Import statements updated
- [ ] Configuration classes migrated
- [ ] Service classes updated
- [ ] Controllers updated
- [ ] Application properties updated

#### Build and Compilation
- [ ] Web module builds successfully
- [ ] Worker module builds successfully
- [ ] No compilation errors
- [ ] No deprecated API warnings
- [ ] Maven dependency conflicts resolved

#### Unit Test Validation
- [ ] All existing unit tests updated for Azure services
- [ ] New unit tests written for Azure-specific functionality
- [ ] Test coverage maintained or improved
- [ ] All unit tests passing
- [ ] Mock objects updated for Azure services

**Test Execution Commands:**
```bash
mvn clean test -Dtest=*AzureBlobServiceTest
mvn clean test -Dtest=*ServiceBusTest
mvn test
```

### Phase 4: Integration Testing

#### Local Integration Testing
- [ ] Application starts successfully with Azure configuration
- [ ] Database connectivity verified
- [ ] File upload to Azure Blob Storage working
- [ ] File download from Azure Blob Storage working
- [ ] Message publishing to Service Bus working
- [ ] Message consumption from Service Bus working

#### Azure Environment Integration Testing
- [ ] Applications deployed to Azure staging environment
- [ ] End-to-end file upload/processing workflow tested
- [ ] Message flow between web and worker applications verified
- [ ] Error handling and retry logic tested
- [ ] Performance testing completed
- [ ] Load testing executed

**Integration Test Scenarios:**
```java
@Test
@DisplayName("End-to-end file processing workflow")
void testFileProcessingWorkflow() {
    // 1. Upload file via web interface
    // 2. Verify file stored in Azure Blob Storage
    // 3. Verify message sent to Service Bus
    // 4. Verify worker processes message
    // 5. Verify database updated with processing results
}
```

---

## Application Validation

### Functional Testing

#### Web Application Features
- [ ] Home page loads correctly
- [ ] File upload functionality works
- [ ] File listing displays correctly
- [ ] File download works
- [ ] Search functionality operational
- [ ] User authentication (if applicable) working
- [ ] Error pages display correctly

#### Worker Application Features
- [ ] Message consumption working
- [ ] Image processing functionality intact
- [ ] Database updates successful
- [ ] Error handling working
- [ ] Retry logic functional
- [ ] Logging operational

#### API Endpoints Testing
- [ ] `POST /storage/upload` - File upload
- [ ] `GET /storage/download/{blobName}` - File download
- [ ] `GET /storage/presigned-url/{blobName}` - Generate SAS URL
- [ ] `GET /health` - Health check endpoint
- [ ] `GET /actuator/info` - Application info

**API Test Examples:**
```bash
# File upload test
curl -X POST -F "file=@test-image.jpg" http://localhost:8080/storage/upload

# File download test
curl -O http://localhost:8080/storage/download/test-image.jpg

# Health check
curl http://localhost:8080/actuator/health
```

### Performance Testing

#### Response Time Validation
- [ ] Home page load time < 2 seconds
- [ ] File upload response time acceptable for file sizes
- [ ] File download response time meets requirements
- [ ] API response times within SLA
- [ ] Database query performance acceptable

#### Throughput Testing
- [ ] Concurrent file uploads handled correctly
- [ ] Message processing throughput meets requirements
- [ ] Database can handle expected load
- [ ] No memory leaks under sustained load
- [ ] Resource utilization within expected ranges

#### Scalability Testing
- [ ] Application scales with increased load
- [ ] Auto-scaling policies working (if configured)
- [ ] Database connection pooling effective
- [ ] Message processing scales with queue depth

**Performance Test Tools:**
```bash
# JMeter test plan execution
jmeter -n -t azure-migration-load-test.jmx -l results.jtl

# Azure CLI monitoring
az monitor metrics list --resource assets-manager-web --metric "Http Requests"
```

### Security Testing

#### Authentication and Authorization
- [ ] Azure Managed Identity working correctly
- [ ] Access to storage resources properly restricted
- [ ] Service Bus access controls working
- [ ] Database access properly secured
- [ ] No hardcoded secrets in application

#### Data Security
- [ ] Data encrypted in transit (HTTPS/TLS)
- [ ] Data encrypted at rest in storage
- [ ] Database connections use SSL
- [ ] Sensitive data not logged
- [ ] PII handling compliant with requirements

#### Network Security
- [ ] Firewall rules properly configured
- [ ] Network access restrictions working
- [ ] CORS policies correctly implemented
- [ ] Security headers present in HTTP responses

**Security Validation Commands:**
```bash
# Check SSL certificate
openssl s_client -connect assets-manager-web.azurewebsites.net:443

# Verify HTTPS redirect
curl -I http://assets-manager-web.azurewebsites.net

# Check security headers
curl -I https://assets-manager-web.azurewebsites.net
```

---

## Production Readiness Validation

### Monitoring and Logging

#### Application Insights Integration
- [ ] Application Insights collecting telemetry
- [ ] Custom events and metrics being tracked
- [ ] Application map showing dependencies
- [ ] Performance counters being collected
- [ ] Exception tracking operational

#### Log Analysis
- [ ] Application logs flowing to Azure Monitor
- [ ] Log levels appropriate for production
- [ ] Structured logging implemented
- [ ] Log queries working in Kusto
- [ ] Alerts configured for critical errors

#### Metrics and Dashboards
- [ ] Key performance indicators defined
- [ ] Custom dashboards created
- [ ] Alerting rules configured
- [ ] Notification channels tested
- [ ] SLA monitoring in place

### Backup and Disaster Recovery

#### Database Backup
- [ ] Automated backup schedule configured
- [ ] Point-in-time recovery tested
- [ ] Backup retention policy set
- [ ] Cross-region backup (if required)
- [ ] Restore procedure documented and tested

#### Storage Backup
- [ ] Blob storage backup strategy implemented
- [ ] Geo-redundant storage configured (if required)
- [ ] Data recovery procedures tested
- [ ] Version control for critical files

#### Application Recovery
- [ ] Application deployment automation tested
- [ ] Configuration backup and restore procedures
- [ ] Database migration scripts version controlled
- [ ] Infrastructure as Code (ARM templates) available
- [ ] Disaster recovery runbook created

### Operational Procedures

#### Deployment Process
- [ ] CI/CD pipeline operational
- [ ] Blue-green deployment capability
- [ ] Rollback procedures tested
- [ ] Environment promotion process defined
- [ ] Change management process in place

#### Support Documentation
- [ ] Operational runbooks created
- [ ] Troubleshooting guides available
- [ ] Architecture documentation updated
- [ ] API documentation current
- [ ] Contact information for escalation

#### Team Readiness
- [ ] Development team trained on Azure services
- [ ] Operations team familiar with Azure tools
- [ ] Support procedures updated
- [ ] Incident response procedures tested
- [ ] Knowledge transfer sessions completed

---

## Final Go-Live Validation

### Pre-Cutover Checklist

#### Technical Readiness
- [ ] All validation tests passing
- [ ] Performance benchmarks met
- [ ] Security requirements satisfied
- [ ] Monitoring and alerting operational
- [ ] Backup and recovery procedures validated

#### Business Readiness
- [ ] Stakeholder signoff obtained
- [ ] User training completed (if required)
- [ ] Communication plan executed
- [ ] Support team ready
- [ ] Rollback plan approved

#### Cutover Preparation
- [ ] DNS change preparation completed
- [ ] Load balancer configuration ready
- [ ] Traffic routing plan finalized
- [ ] Maintenance window scheduled
- [ ] Status page prepared

### Post-Cutover Validation

#### Immediate Validation (0-1 hours)
- [ ] Application accessible via new Azure URLs
- [ ] All core functionality working
- [ ] No critical errors in logs
- [ ] Database connectivity confirmed
- [ ] File storage operations working
- [ ] Message processing operational

#### Short-term Validation (1-24 hours)
- [ ] User traffic successfully processed
- [ ] Background processes running normally
- [ ] Performance within expected ranges
- [ ] No data loss incidents
- [ ] Monitoring systems operational
- [ ] Support tickets triaged and resolved

#### Long-term Validation (1-7 days)
- [ ] System stability confirmed
- [ ] Performance optimization completed
- [ ] Cost monitoring in place
- [ ] User feedback collected and addressed
- [ ] Final documentation updates completed
- [ ] Project closure activities initiated

### Success Criteria Verification

#### Technical Success Metrics
- [ ] 99.9% uptime achieved
- [ ] Response times meet or exceed baseline
- [ ] Zero data loss during migration
- [ ] All functional requirements satisfied
- [ ] Security compliance maintained

#### Business Success Metrics
- [ ] User satisfaction maintained or improved
- [ ] Business processes uninterrupted
- [ ] Cost targets achieved
- [ ] Performance improvements realized
- [ ] Migration completed within timeline

#### Final Approval
- [ ] Technical team signoff
- [ ] Business stakeholder approval
- [ ] Security team approval
- [ ] Operations team acceptance
- [ ] Project manager closure approval

---

## Troubleshooting Common Issues

### Database Connection Issues
```bash
# Test database connectivity
psql "host=assets-manager-db.postgres.database.azure.com port=5432 dbname=assets_manager user=adminuser@assets-manager-db sslmode=require"

# Check firewall rules
az postgres server firewall-rule list --resource-group assets-manager-rg --server-name assets-manager-db
```

### Storage Access Issues
```bash
# Test storage account access
az storage blob list --account-name assetsmanagerstorage --container-name assets-container

# Check managed identity permissions
az role assignment list --assignee <managed-identity-principal-id> --scope /subscriptions/<sub-id>/resourceGroups/assets-manager-rg/providers/Microsoft.Storage/storageAccounts/assetsmanagerstorage
```

### Service Bus Issues
```bash
# Check Service Bus namespace
az servicebus namespace show --name assets-manager-servicebus --resource-group assets-manager-rg

# List topics and subscriptions
az servicebus topic list --namespace-name assets-manager-servicebus --resource-group assets-manager-rg
az servicebus topic subscription list --namespace-name assets-manager-servicebus --topic-name image-processing-topic --resource-group assets-manager-rg
```

### Application Insights Issues
```bash
# Check Application Insights configuration
az monitor app-insights component show --app assets-manager-web --resource-group assets-manager-rg

# Query application logs
az monitor log-analytics query --workspace <workspace-id> --analytics-query "traces | where timestamp > ago(1h) | order by timestamp desc"
```

This comprehensive validation checklist ensures that every aspect of the Azure migration is thoroughly tested and validated before, during, and after the migration process.