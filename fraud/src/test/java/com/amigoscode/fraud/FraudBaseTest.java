package com.amigoscode.fraud;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
public class FraudBaseTest {

    @Autowired
    private FraudController fraudController;

    @MockBean
    private FraudCheckService fraudCheckService;

    @BeforeEach
    public void setup() {
        RestAssuredMockMvc.standaloneSetup(fraudController);

        // When any customer id is evaluated, stub it to false
        Mockito.when(fraudCheckService.isFraudulentCustomer(Mockito.anyInt())).thenReturn(false);
    }
}
