package com.amigoscode.customer;

import com.amigoscode.clients.notification.NotificationClient;
import com.amigoscode.clients.notification.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@AutoConfigureStubRunner(
        ids = {"com.amigoscode:notification:+:stubs:8082"},
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
public class NotificationClientContractTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.amigoscode.customer.CustomerRepository customerRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Test
    public void shouldAcceptNotificationRequest() {
        NotificationRequest request = new NotificationRequest(
                1,
                "alex.doe@example.com",
                "Hi Alex, welcome to Amigoscode..."
        );

        // Expect successful void execution (200 OK)
        assertDoesNotThrow(() -> notificationClient.sendNotification(request));
    }
}
