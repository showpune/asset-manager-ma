package com.microsoft.migration.assets.config;

import com.azure.spring.cloud.autoconfigure.implementation.servicebus.properties.AzureServiceBusProperties;
import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.spring.messaging.implementation.annotation.EnableAzureMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Service Bus Configuration
 * Replaces RabbitConfig for Azure migration
 */
@Configuration
@EnableAzureMessaging
@Slf4j
public class AzureServiceBusConfig {

    @Value("${spring.cloud.azure.servicebus.namespace}")
    private String serviceBusNamespace;

    /**
     * Create ServiceBusAdministrationClient using Azure Managed Identity
     */
    @Bean
    public ServiceBusAdministrationClient serviceBusAdminClient(
            AzureServiceBusProperties properties, 
            TokenCredential credential) {
        
        log.info("Initializing Service Bus Administration Client for namespace: {}", serviceBusNamespace);
        
        return new ServiceBusAdministrationClientBuilder()
                .credential(properties.getFullyQualifiedNamespace(), credential)
                .buildClient();
    }

    /**
     * Create image processing queue if it doesn't exist
     */
    @Bean
    public QueueProperties imageProcessingQueue(ServiceBusAdministrationClient adminClient) {
        String queueName = "image-processing";
        
        try {
            QueueProperties queue = adminClient.getQueue(queueName);
            log.info("Using existing Service Bus queue: {}", queueName);
            return queue;
        } catch (ResourceNotFoundException e) {
            log.info("Creating new Service Bus queue: {}", queueName);
            QueueProperties queue = adminClient.createQueue(queueName);
            log.info("Successfully created Service Bus queue: {}", queueName);
            return queue;
        }
    }
}