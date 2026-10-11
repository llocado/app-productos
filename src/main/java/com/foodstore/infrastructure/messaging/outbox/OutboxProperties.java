package com.foodstore.infrastructure.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** productos.outbox.*: frecuencia del publicador, tamano del lote y espera de la confirmacion de RabbitMQ. */
@ConfigurationProperties(prefix = "productos.outbox")
public record OutboxProperties(Duration intervalo, int lote, Duration esperaConfirmacion) {
}
