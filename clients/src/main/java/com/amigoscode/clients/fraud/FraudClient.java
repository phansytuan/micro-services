package com.amigoscode.clients.fraud;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "fraud",
        url = "${clients.fraud.url}"
)
public interface FraudClient {

    @GetMapping(path = "api/v1/fraud-check/{customerId}")
    FraudCheckResponse isFraudster(
            @PathVariable("customerId") Integer customerId
    );
}

/**  Load Balancer & Service Discovery: giúp Feign biết được địa chỉ IP thực sự của dịch vụ cần gọi là gì.
 *
 * Thay vì gọi cứng địa chỉ http://192.168..., Feign chỉ cần gọi theo tên dịch vụ (ví dụ: CUSTOMER-SERVICE),
 *  và các thành phần này sẽ dẫn đường đến đúng nơi.
 */