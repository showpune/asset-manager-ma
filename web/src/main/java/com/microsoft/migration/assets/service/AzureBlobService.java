package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.migration.assets.model.ImageMetadata;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.ServiceBusConfig.QUEUE_NAME;

@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
public class AzureBlobService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final ServiceBusTemplate serviceBusTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container}")
    private String containerName;

    private BlobContainerClient getContainerClient() {
        return blobServiceClient.getBlobContainerClient(containerName);
    }

    @Override
    public List<S3StorageItem> listObjects() {
        BlobContainerClient containerClient = getContainerClient();

        return containerClient.listBlobs().stream()
                .map(blobItem -> {
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
                            generateUrl(blobName)
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        String key = generateKey(file.getOriginalFilename());
        
        BlobClient blobClient = getContainerClient().getBlobClient(key);
        
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType());
        
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(file.getInputStream())
                .setHeaders(headers);
        
        blobClient.uploadWithResponse(options, null, null);

        // Send message to queue for thumbnail generation
        ImageProcessingMessage processingMessage = new ImageProcessingMessage(
            key,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        org.springframework.messaging.Message<ImageProcessingMessage> message = MessageBuilder.withPayload(processingMessage).build();
        serviceBusTemplate.send(QUEUE_NAME, message);

        // Create and save metadata to database
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(key);
        metadata.setS3Url(generateUrl(key));
        
        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        BlobClient blobClient = getContainerClient().getBlobClient(key);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStream(outputStream);
        
        return new java.io.ByteArrayInputStream(outputStream.toByteArray());
    }

    @Override
    public void deleteObject(String key) throws IOException {
        BlobContainerClient containerClient = getContainerClient();
        
        // Delete the blob
        BlobClient blobClient = containerClient.getBlobClient(key);
        blobClient.deleteIfExists();

        try {
            // Try to delete thumbnail if it exists
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
        // Extract filename from the object key
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }
    
    private String generateUrl(String key) {
        BlobClient blobClient = getContainerClient().getBlobClient(key);
        return blobClient.getBlobUrl();
    }

    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
}
