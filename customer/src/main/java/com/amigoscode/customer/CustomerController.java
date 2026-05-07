package com.amigoscode.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("api/v1/customers")
@AllArgsConstructor
@Tag(name = "Customer", description = "API for customer registration and management")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Register a new customer",
               description = "Creates a new customer and performs fraud check")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer registered successfully"),
        @ApiResponse(responseCode = "500", description = "Fraud detected - customer is a fraudster")
    })
    public void registerCustomer(
            @Parameter(description = "Customer registration details", required = true)
            @RequestBody CustomerRegistrationRequest customerRegistrationRequest
    ) {
        log.info("new customer registration {}", customerRegistrationRequest);
        customerService.registerCustomer(customerRegistrationRequest);
    }
}
