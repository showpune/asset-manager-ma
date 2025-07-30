package com.microsoft.migration.assets.worker.service;

import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
import com.azure.spring.messaging.servicebus.support.ServiceBusMessageHeaders;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Azure Service Bus message listener for image processing
 * Replaces RabbitMQ message listener for Azure migration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AzureImageProcessingListener {

    private final FileProcessor fileProcessor;

    /**
     * Listen to image processing messages from Azure Service Bus
     */
    @ServiceBusListener(destination = "image-processing")
    public void processImageMessage(
            ImageProcessingMessage message,
            @Header(ServiceBusMessageHeaders.RECEIVED_MESSAGE_CONTEXT) ServiceBusReceivedMessageContext context) {
        
        log.info("Received image processing message for key: {}, type: {}, storage: {}", 
                message.getKey(), message.getContentType(), message.getStorageType());
        
        try {
            // Process the image based on storage type
            if ("azure-blob".equals(message.getStorageType())) {
                fileProcessor.processAzureBlobImage(message);
            } else {
                // Fallback for other storage types during migration
                fileProcessor.processImage(message);
            }
            
            // Complete the message to remove it from the queue
            context.complete();
            log.info("Successfully processed image: {}", message.getKey());
            
        } catch (Exception e) {
            log.error("Error processing image: {} - Error: {}", message.getKey(), e.getMessage(), e);
            
            // Abandon the message to retry later
            context.abandon();
        }
    }
}