import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should accept notification request")
    
    request {
        method POST()
        url "/api/v1/notification"
        headers {
            contentType applicationJson()
        }
        body([
            toCustomerId: 1,
            toCustomerName: "alex.doe@example.com",
            message: "Hi Alex, welcome to Amigoscode..."
        ])
    }
    
    response {
        status OK()
    }
}
