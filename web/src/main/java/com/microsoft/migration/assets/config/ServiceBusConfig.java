package com.microsoft.migration.assets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;

@Configuration
@EnableJms
public class ServiceBusConfig {
    public static final String QUEUE_NAME = "image-processing";
}
