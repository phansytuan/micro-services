package com.amigoscode.clients.fraud;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for fraud check")
public record FraudCheckResponse(
        @Schema(description = "Indicates if the customer is a fraudster", example = "false")
        Boolean isFraudster) {
}
