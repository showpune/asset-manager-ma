# Asset Manager Application - Azure Migration

This repository contains a Spring Boot microservices application for managing file uploads and thumbnail generation, along with comprehensive documentation for migrating from AWS to Azure infrastructure.

## Current Architecture

- **Web Module**: Handles file uploads, serves content, manages metadata
- **Worker Module**: Processes thumbnail generation asynchronously  
- **Technology Stack**: Spring Boot 3.4.3, AWS S3, RabbitMQ, PostgreSQL

## Migration Documentation

### 📋 [Azure Migration Assessment Report](./AZURE_MIGRATION_ASSESSMENT.md)
Complete technical assessment with architecture analysis, migration sequence, risk assessment, timeline, and cost analysis.

### 📊 [Executive Summary](./AZURE_MIGRATION_SUMMARY.md)
High-level overview with key benefits, timeline, costs, and resource requirements for stakeholders.

### 🛠️ [Technical Implementation Guide](./AZURE_MIGRATION_TECHNICAL_GUIDE.md)
Detailed code examples, step-by-step implementation instructions, testing strategies, and rollback procedures for developers.

## Key Migration Benefits

- **15% cost reduction** through Azure's competitive pricing
- **Enhanced security** with Azure Managed Identity (keyless authentication)
- **Auto-scaling capabilities** with Azure App Service
- **Reduced operational overhead** with fully managed services
- **Improved monitoring** with Azure Application Insights

## Migration Timeline

**Total Duration: 10 weeks (50 days)**

1. **Foundation Setup** (8 days) - Azure subscription, identity, resources
2. **Storage Migration** (10 days) - AWS S3 to Azure Blob Storage
3. **Messaging Migration** (10 days) - RabbitMQ to Azure Service Bus  
4. **Database Migration** (4 days) - PostgreSQL to Azure Database
5. **Application Deployment** (8 days) - Deploy to Azure App Service
6. **Operations & Go-Live** (10 days) - Monitoring, validation, production

## Quick Start

### Current Application
```bash
# Build and run current application
mvn clean package
java -jar web/target/assets-manager-web-0.0.1-SNAPSHOT.jar
java -jar worker/target/assets-manager-worker-0.0.1-SNAPSHOT.jar
```

### Migration Process
1. Review the [Assessment Report](./AZURE_MIGRATION_ASSESSMENT.md) for complete analysis
2. Follow the [Technical Guide](./AZURE_MIGRATION_TECHNICAL_GUIDE.md) for implementation
3. Use the [Executive Summary](./AZURE_MIGRATION_SUMMARY.md) for stakeholder communication

## Support

For questions about the migration:
- Review the technical documentation in this repository
- Consult the risk mitigation strategies in the assessment report
- Follow the phased approach outlined in the implementation guide

---

**Note**: This migration assessment was generated to provide a comprehensive roadmap for moving from AWS to Azure infrastructure while maintaining application functionality and improving operational efficiency.