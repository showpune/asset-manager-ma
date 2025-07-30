package com.microsoft.migration.assets.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Azure Blob Storage Configuration
 * Replaces AwsS3Config for Azure migration
 */
@Configuration
@Profile("!dev") // Active when not in dev profile
@Slf4j
public class AzureBlobStorageConfig {

    @Value("${azure.storage.account-name}")
    private String storageAccountName;

    @Value("${azure.storage.container-name}")
    private String containerName;

    /**
     * Create BlobServiceClient using Azure Managed Identity
     */
    @Bean
    public BlobServiceClient blobServiceClient() {
        String endpoint = String.format("https://%s.blob.core.windows.net", storageAccountName);
        
        log.info("Initializing Azure Blob Service Client with endpoint: {}", endpoint);
        
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    /**
     * Create and ensure the blob container exists
     */
    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        log.info("Creating blob container client for container: {}", containerName);
        
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
            log.info("Created new blob container: {}", containerName);
        } else {
            log.info("Using existing blob container: {}", containerName);
        }
        
        return containerClient;
    }
}