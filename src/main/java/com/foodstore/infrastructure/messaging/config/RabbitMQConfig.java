package com.foodstore.infrastructure.messaging.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

/**
 * Topologia RabbitMQ de app-productos (ver "Contrato de eventos" en el ROADMAP): un exchange topic propio
 * (productos.events) del que es productor, y una cola propia con su dead-letter queue para consumir pago.aprobado de
 * pagos.events. El exchange de pagos se declara de forma defensiva e idempotente (pagos-service lo declara igual):
 * asi la cola se puede enlazar aunque pagos no haya arrancado todavia.
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_PRODUCTOS_EVENTS = "productos.events";
    public static final String EXCHANGE_PAGOS_EVENTS = "pagos.events";

    public static final String ROUTING_KEY_PAGO_APROBADO = "pago.aprobado";
    public static final String ROUTING_KEY_STOCK_DESCUENTO_RECHAZADO = "stock.descuento-rechazado";

    public static final String QUEUE_PAGO_APROBADO = "productos.pago-aprobado";
    private static final String DLX_PRODUCTOS = "productos.dlx";
    private static final String DLQ_PAGO_APROBADO = "productos.pago-aprobado.dlq";

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TopicExchange productosEventsExchange() {
        return new TopicExchange(EXCHANGE_PRODUCTOS_EVENTS);
    }

    @Bean
    public TopicExchange pagosEventsExchange() {
        return new TopicExchange(EXCHANGE_PAGOS_EVENTS);
    }

    @Bean
    public DirectExchange productosDeadLetterExchange() {
        return new DirectExchange(DLX_PRODUCTOS);
    }

    @Bean
    public Queue pagoAprobadoDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_PAGO_APROBADO).build();
    }

    @Bean
    public Binding pagoAprobadoDeadLetterBinding() {
        return BindingBuilder.bind(pagoAprobadoDeadLetterQueue()).to(productosDeadLetterExchange()).with(DLQ_PAGO_APROBADO);
    }

    @Bean
    public Queue pagoAprobadoQueue() {
        return QueueBuilder.durable(QUEUE_PAGO_APROBADO)
                .withArgument("x-dead-letter-exchange", DLX_PRODUCTOS)
                .withArgument("x-dead-letter-routing-key", DLQ_PAGO_APROBADO)
                .build();
    }

    @Bean
    public Binding pagoAprobadoBinding() {
        return BindingBuilder.bind(pagoAprobadoQueue()).to(pagosEventsExchange()).with(ROUTING_KEY_PAGO_APROBADO);
    }

    /**
     * Los reintentos son para fallos transitorios (incluye un conflicto de concurrencia al descontar stock: al
     * reintentar se lee el stock nuevo). No se reintenta un mensaje que el listener ya rechazo de forma definitiva
     * (AmqpRejectAndDontRequeueException, a veces envuelta en otra excepcion) ni uno que no se puede convertir
     * (por ejemplo, JSON ilegible): van directo a la DLQ.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer noReintentarRechazosDefinitivos() {
        return settings -> settings.setExceptionPredicate(error -> !esIrrecuperable(error));
    }

    /**
     * Reemplaza a RejectAndDontRequeueRecoverer (el de Spring), que al agotar los reintentos escribe el cuerpo del
     * mensaje y ~80 lineas de stack trace. Este deja una sola linea con la cola, el messageId y la clase de la causa
     * (sin su texto: el de un error de JSON puede contener fragmentos del payload) y rechaza igual: va a la DLQ.
     */
    @Bean
    public MessageRecoverer recuperadorSinCuerpoEnElLog() {
        return (mensaje, causa) -> {
            log.warn("Reintentos agotados, mensaje a la DLQ cola={} messageId={} causa={}",
                    mensaje.getMessageProperties().getConsumerQueue(), mensaje.getMessageProperties().getMessageId(),
                    NestedExceptionUtils.getMostSpecificCause(causa).getClass().getSimpleName());
            throw new ListenerExecutionFailedException("Retry Policy Exhausted", new AmqpRejectAndDontRequeueException(causa), mensaje);
        };
    }

    private static boolean esIrrecuperable(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause() == actual ? null : actual.getCause()) {
            if (actual instanceof AmqpRejectAndDontRequeueException
                    || actual instanceof MessageConversionException
                    || actual instanceof org.springframework.messaging.converter.MessageConversionException
                    || actual instanceof ClassCastException
                    || actual instanceof NoSuchMethodException) {
                return true;
            }
        }
        return false;
    }
}
