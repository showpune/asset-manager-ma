package com.microsoft.migration.assets.config;

import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class TestServiceBusConfig {

    @Bean
    @Primary
    public ServiceBusTemplate serviceBusTemplate() {
        return Mockito.mock(ServiceBusTemplate.class);
    }
}
