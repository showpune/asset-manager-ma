package com.microsoft.migration.assets.service;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.microsoft.migration.assets.model.ImageMetadata;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Azure Blob Storage implementation of StorageService interface.
 * This replaces the AWS S3 implementation for Azure migration.
 */
@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
@Slf4j
public class AzureBlobStorageService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final ServiceBusTemplate serviceBusTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Override
    public List<S3StorageItem> listObjects() {
        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            
            return containerClient.listBlobs().stream()
                    .map(this::convertBlobToStorageItem)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing blobs from container: {}", containerName, e);
            throw new RuntimeException("Failed to list objects from Azure Blob Storage", e);
        }
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String blobName = generateBlobName(file.getOriginalFilename());
        
        try {
            BlobClient blobClient = blobServiceClient
                    .getBlobContainerClient(containerName)
                    .getBlobClient(blobName);
            
            // Upload file to Azure Blob Storage
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(file.getContentType());
            BlobUploadFromFileOptions uploadOptions = new BlobUploadFromFileOptions(file.getOriginalFilename())
                    .setHeaders(headers);
            
            blobClient.upload(file.getInputStream(), file.getSize(), true);
            log.info("Successfully uploaded blob: {} to container: {}", blobName, containerName);

            // Send message to Azure Service Bus for thumbnail generation
            ImageProcessingMessage processingMessage = new ImageProcessingMessage(
                blobName,
                file.getContentType(),
                getStorageType(),
                file.getSize()
            );
            
            Message<ImageProcessingMessage> serviceBusMessage = MessageBuilder
                    .withPayload(processingMessage)
                    .build();
            
            serviceBusTemplate.send("image-processing", serviceBusMessage);
            log.info("Sent processing message to Service Bus for blob: {}", blobName);

            // Create and save metadata to database
            saveImageMetadata(file, blobName);
            
        } catch (Exception e) {
            log.error("Error uploading file: {} to Azure Blob Storage", file.getOriginalFilename(), e);
            throw new IOException("Failed to upload file to Azure Blob Storage", e);
        }
    }

    @Override
    public InputStream getObject(String blobName) throws IOException {
        try {
            BlobClient blobClient = blobServiceClient
                    .getBlobContainerClient(containerName)
                    .getBlobClient(blobName);
            
            return blobClient.openInputStream();
        } catch (Exception e) {
            log.error("Error downloading blob: {} from container: {}", blobName, containerName, e);
            throw new IOException("Failed to download blob from Azure Blob Storage", e);
        }
    }

    @Override
    public void deleteObject(String blobName) throws IOException {
        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            
            // Delete original blob
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();
            log.info("Deleted blob: {} from container: {}", blobName, containerName);

            // Try to delete thumbnail if it exists
            String thumbnailName = getThumbnailBlobName(blobName);
            BlobClient thumbnailClient = containerClient.getBlobClient(thumbnailName);
            thumbnailClient.deleteIfExists();
            log.info("Deleted thumbnail blob: {} from container: {}", thumbnailName, containerName);

            // Delete metadata from database
            deleteImageMetadata(blobName);
            
        } catch (Exception e) {
            log.error("Error deleting blob: {} from container: {}", blobName, containerName, e);
            throw new IOException("Failed to delete blob from Azure Blob Storage", e);
        }
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }

    /**
     * Convert Azure BlobItem to S3StorageItem for backward compatibility
     */
    private S3StorageItem convertBlobToStorageItem(BlobItem blobItem) {
        // Get upload time from metadata if available
        Instant uploadedAt = imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(blobItem.getName()))
                .map(metadata -> metadata.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .findFirst()
                .orElse(blobItem.getProperties().getLastModified());

        return new S3StorageItem(
                blobItem.getName(),
                extractFilename(blobItem.getName()),
                blobItem.getProperties().getContentLength(),
                blobItem.getProperties().getLastModified(),
                uploadedAt,
                generateBlobUrl(blobItem.getName())
        );
    }

    /**
     * Extract filename from blob name
     */
    private String extractFilename(String blobName) {
        int lastDashIndex = blobName.lastIndexOf('-');
        return lastDashIndex >= 0 ? blobName.substring(lastDashIndex + 1) : blobName;
    }

    /**
     * Generate secure URL for blob access
     */
    private String generateBlobUrl(String blobName) {
        try {
            BlobClient blobClient = blobServiceClient
                    .getBlobContainerClient(containerName)
                    .getBlobClient(blobName);
            
            // Generate SAS URL for secure access (valid for 1 hour)
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues()
                    .setExpiryTime(Instant.now().plusSeconds(3600))
                    .setPermissions("r"); // Read permission only
            
            return blobClient.getBlobUrl() + "?" + blobClient.generateSas(sasValues);
        } catch (Exception e) {
            log.warn("Could not generate SAS URL for blob: {}, returning direct URL", blobName);
            return blobServiceClient.getBlobContainerClient(containerName)
                    .getBlobClient(blobName)
                    .getBlobUrl();
        }
    }

    /**
     * Generate unique blob name for uploaded file
     */
    private String generateBlobName(String originalFilename) {
        return UUID.randomUUID().toString() + "-" + originalFilename;
    }

    /**
     * Generate thumbnail blob name from original blob name
     */
    private String getThumbnailBlobName(String originalBlobName) {
        return "thumbnails/" + originalBlobName;
    }

    /**
     * Save image metadata to database
     */
    private void saveImageMetadata(MultipartFile file, String blobName) {
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(blobName); // Keeping field name for backward compatibility
        metadata.setS3Url(generateBlobUrl(blobName)); // Keeping field name for backward compatibility
        
        imageMetadataRepository.save(metadata);
        log.info("Saved metadata for blob: {}", blobName);
    }

    /**
     * Delete image metadata from database
     */
    private void deleteImageMetadata(String blobName) {
        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(blobName))
                .findFirst()
                .ifPresent(metadata -> {
                    imageMetadataRepository.delete(metadata);
                    log.info("Deleted metadata for blob: {}", blobName);
                });
    }
}