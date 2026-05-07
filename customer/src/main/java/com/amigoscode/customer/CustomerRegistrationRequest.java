package com.amigoscode.customer;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request object for customer registration")
public record CustomerRegistrationRequest(
        @Schema(description = "Customer's first name", example = "John", required = true)
        String firstName,
        
        @Schema(description = "Customer's last name", example = "Doe", required = true)
        String lastName,
        
        @Schema(description = "Customer's email address", example = "john.doe@example.com", required = true)
        String email) {
}
