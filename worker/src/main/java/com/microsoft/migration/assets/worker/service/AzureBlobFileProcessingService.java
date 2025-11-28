package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.migration.assets.worker.model.ImageMetadata;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("azure-blob")
@RequiredArgsConstructor
public class AzureBlobFileProcessingService extends AbstractFileProcessingService {
    private final BlobContainerClient blobContainerClient;
    private final ImageMetadataRepository imageMetadataRepository;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        try (OutputStream outputStream = Files.newOutputStream(destination)) {
            blobClient.downloadStream(outputStream);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.uploadFromFile(source.toString(), true);
        
        // Save or update thumbnail metadata
        ImageMetadata metadata = imageMetadataRepository.findById(extractOriginalKey(key))
            .orElseGet(() -> {
                ImageMetadata newMetadata = new ImageMetadata();
                newMetadata.setId(extractOriginalKey(key));
                return newMetadata;
            });

        metadata.setThumbnailKey(key);
        metadata.setThumbnailUrl(generateUrl(key));
        imageMetadataRepository.save(metadata);
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }

    @Override
    protected String generateUrl(String key) {
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.getBlobUrl();
    }

    private String extractOriginalKey(String key) {
        // Remove _thumbnail suffix if present
        String suffix = "_thumbnail";
        int suffixIndex = key.lastIndexOf(suffix);
        if (suffixIndex > 0) {
            return key.substring(0, suffixIndex);
        }
        return key;
    }
}
