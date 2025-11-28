package com.microsoft.migration.assets.config;

import com.azure.storage.blob.BlobContainerClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public JmsTemplate jmsTemplate() {
        return mock(JmsTemplate.class);
    }

    @Bean
    @Primary
    public BlobContainerClient blobContainerClient() {
        return mock(BlobContainerClient.class);
    }
}
