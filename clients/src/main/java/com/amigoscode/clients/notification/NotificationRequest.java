package com.amigoscode.clients.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request object for sending notifications")
public record NotificationRequest(
        @Schema(description = "ID of the customer to notify", example = "1", required = true)
        Integer toCustomerId,
        
        @Schema(description = "Name of the customer to notify", example = "John Doe", required = true)
        String toCustomerName,
        
        @Schema(description = "Message to send", example = "Welcome to our service!", required = true)
        String message
) {
}
