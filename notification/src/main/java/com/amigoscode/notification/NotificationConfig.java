package com.amigoscode.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for notification messaging.
 */
@Configuration
public class NotificationConfig {

    @Value("${rabbitmq.exchanges.internal}")
    private String internalExchange;

    @Value("${rabbitmq.queues.notification}")
    private String notificationQueue;

    @Value("${rabbitmq.routing-keys.internal-notification}")
    private String internalNotificationRoutingKey;

    /**
     * Defines a Topic Exchange.
     * TopicExchange allows routing messages based on pattern matching of RoutingKeys.
     */
    @Bean
    public TopicExchange internalTopicExchange() {
        return new TopicExchange(this.internalExchange);
    }

    /**
     * Defines the Notification Queue.
     * This is where messages will be stored until consumed.
     */
    @Bean
    public Queue notificationQueue() {
        return new Queue(this.notificationQueue);
    }

    /**
     * Binds the notification queue to the internal exchange using a RoutingKey.

     * Flow:
     * Producer -> Exchange -> (RoutingKey match) -> Queue -> Consumer
     */
    @Bean
    public Binding internalToNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(internalTopicExchange())
                .with(this.internalNotificationRoutingKey);
    }


    public String getInternalExchange() {
        return internalExchange;
    }

    public String getNotificationQueue() {
        return notificationQueue;
    }

    public String getInternalNotificationRoutingKey() {
        return internalNotificationRoutingKey;
    }
}
