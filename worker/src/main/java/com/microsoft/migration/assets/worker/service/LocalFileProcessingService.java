package com.microsoft.migration.assets.worker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File processing service that uses mounted Azure Storage File Share.
 * When deployed to Azure Container Apps, the storage directory should point to
 * the Azure Storage File Share mount path (e.g., /mnt/azure-fileshare).
 * This allows seamless file operations using Java NIO with Azure-backed storage.
 */
@Service
@Profile("dev")
public class LocalFileProcessingService extends AbstractFileProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalFileProcessingService.class);
    
    @Value("${azure.storage.fileshare.mount-path:${local.storage.directory:../storage}}")
    private String storageDirectory;
    
    private Path rootLocation;
    
    @PostConstruct
    public void init() throws Exception {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        logger.info("File storage directory (Azure File Share mount or local): {}", rootLocation);
        
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            logger.info("Created storage directory");
        }
    }

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        Path sourcePath = rootLocation.resolve(key);
        if (!Files.exists(sourcePath)) {
            throw new java.io.FileNotFoundException("File not found: " + sourcePath);
        }
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String getStorageType() {
        return "azure-fileshare";
    }

    @Override
    protected String generateUrl(String key) {
        // For Azure File Share storage, return relative path
        return "/storage/" + key;
    }
}