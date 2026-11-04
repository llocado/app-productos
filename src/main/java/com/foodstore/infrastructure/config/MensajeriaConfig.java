package com.foodstore.infrastructure.config;

import com.foodstore.infrastructure.messaging.outbox.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas (publicador del outbox y purga de eventos) y sus propiedades (productos.outbox.*, purga.*). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, PurgaProperties.class})
public class MensajeriaConfig {
}
