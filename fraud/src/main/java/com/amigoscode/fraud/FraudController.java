package com.amigoscode.fraud;

import com.amigoscode.clients.fraud.FraudCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/fraud-check")
@AllArgsConstructor
@Slf4j
@Tag(name = "Fraud Check", description = "API for fraud checking and detection")
public class FraudController {

    private final FraudCheckService fraudCheckService;

    @GetMapping(path = "{customerId}")
    @Operation(summary = "Check if customer is a fraudster",
               description = "Returns fraud status for a given customer ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Fraud check completed successfully")
    })
    public FraudCheckResponse isFraudster(
            @Parameter(description = "Customer ID to check", required = true, example = "1")
            @PathVariable("customerId") Integer customerId) {

        boolean isFraudulentCustomer = fraudCheckService.isFraudulentCustomer(customerId);

        log.info("fraud check request for customer {}", customerId);
        return new FraudCheckResponse(isFraudulentCustomer);
    }
}
