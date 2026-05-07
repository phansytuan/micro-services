package com.amigoscode.amqp;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer component responsible for publishing messages to RabbitMQ.
 */
@Component
@Slf4j
@AllArgsConstructor
public class RabbitMQMessageProducer {

    private final AmqpTemplate amqpTemplate;

    /**
     * Publish a message to a specific Exchange with a RoutingKey.
     *
     * @param payload    the message content to be sent
     * @param exchange   the target exchange name
     * @param routingKey the key used by the Exchange to route the message
     *                   to the appropriate Queue(s) based on Bindings
     */
    public void publish(Object payload, String exchange, String routingKey) {

        log.info("Publishing to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
        amqpTemplate.convertAndSend(exchange, routingKey, payload); // to RabbitMQ
        log.info("Published to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
    }

}
