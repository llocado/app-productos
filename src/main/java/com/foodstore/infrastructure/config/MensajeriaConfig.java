package com.foodstore.infrastructure.config;

import com.foodstore.infrastructure.messaging.outbox.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa el publicador del outbox (@Scheduled) y sus propiedades (productos.outbox.*). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class MensajeriaConfig {
}
