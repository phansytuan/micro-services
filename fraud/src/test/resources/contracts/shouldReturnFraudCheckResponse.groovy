import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("should return fraud check response")
    
    request {
        method GET()
        url "/api/v1/fraud-check/1"
    }
    
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            isFraudster: value(consumer(false), producer(false))
        ])
    }
}
