package com.amigoscode.customer;

import com.amigoscode.clients.fraud.FraudCheckResponse;
import com.amigoscode.clients.fraud.FraudClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@AutoConfigureStubRunner(
        ids = {"com.amigoscode:fraud:+:stubs:8081"},
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
public class FraudClientContractTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.amigoscode.customer.CustomerRepository customerRepository;

    @Autowired
    private FraudClient fraudClient;

    @Test
    public void shouldReturnFraudCheckResponse() {
        FraudCheckResponse response = fraudClient.isFraudster(1);

        assertThat(response).isNotNull();
        assertThat(response.isFraudster()).isFalse();
    }
}
