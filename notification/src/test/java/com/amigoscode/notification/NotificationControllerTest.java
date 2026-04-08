package com.amigoscode.notification;

import com.amigoscode.clients.notification.NotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.NestedServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link NotificationController}.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>POST /api/v1/notification — send a notification to a customer</li>
 * </ul>
 *
 * <p>Request body contract:
 * <pre>
 * {
 *   "toCustomerId": Integer,
 *   "toCustomerName": String,   // holds the customer email in practice
 *   "message": String
 * }
 * </pre>
 *
 * <p>Response: 200 OK with empty body (void handler).
 *
 * <p>Test Strategy:
 * <ul>
 *   <li>@WebMvcTest slices the Spring context to the web layer only.</li>
 *   <li>NotificationService is mocked; its void send() is stubbed.</li>
 *   <li>eureka.client.enabled=false suppresses Eureka auto-register noise.</li>
 * </ul>
 */
@WebMvcTest(controllers = NotificationController.class, properties = {
        "eureka.client.enabled=false"
})
@DisplayName("NotificationController – POST /api/v1/notification")
class NotificationControllerTest {

    private static final String BASE_URL = "/api/v1/notification";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    // ─────────────────────── POSITIVE CASES ───────────────────────────────

    /**
     * TC-NOTIF-001: Valid full payload → 200 OK, service invoked once.
     * All three required fields are present and well-formed.
     */
    @Test
    @DisplayName("TC-NOTIF-001: Valid full payload returns 200 OK and delegates to service")
    void shouldSendNotificationSuccessfully() throws Exception {
        doNothing().when(notificationService).send(any(NotificationRequest.class));

        String payload = """
                {
                    "toCustomerId": 1,
                    "toCustomerName": "alex.doe@example.com",
                    "message": "Hi Alex, welcome to Amigoscode!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).send(any(NotificationRequest.class));
    }

    /**
     * TC-NOTIF-002: Payload with only required toCustomerId provided → 200 OK.
     * The controller has no @Valid constraint, so partial payloads reach the service.
     */
    @Test
    @DisplayName("TC-NOTIF-002: Partial payload (toCustomerId only) accepted — 200 OK")
    void shouldAcceptPartialPayloadDueToNoValidationConstraint() throws Exception {
        doNothing().when(notificationService).send(any());

        String payload = """
                {
                    "toCustomerId": 42
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    /**
     * TC-NOTIF-003: Empty JSON object {} → 200 OK.
     * All record fields will be null; no validation enforced by the controller.
     */
    @Test
    @DisplayName("TC-NOTIF-003: Empty JSON object {} accepted (no validation) — 200 OK")
    void shouldAcceptEmptyJsonObjectWithNoValidation() throws Exception {
        doNothing().when(notificationService).send(any());

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    /**
     * TC-NOTIF-004: Service throws RuntimeException.
     * In @WebMvcTest without a @ControllerAdvice, MockMvc re-throws unhandled controller
     * exceptions wrapped in NestedServletException instead of returning an HTTP 500 response.
     * We assert the exception type and its cause to verify the error propagates correctly.
     */
    @Test
    @DisplayName("TC-NOTIF-004: Service RuntimeException → NestedServletException propagates")
    void shouldPropagateNestedServletExceptionWhenServiceThrowsRuntimeException() {
        doThrow(new RuntimeException("Persistence failure"))
                .when(notificationService).send(any());

        String payload = """
                {
                    "toCustomerId": 1,
                    "toCustomerName": "alex.doe@example.com",
                    "message": "Hi Alex!"
                }
                """;

        NestedServletException ex = assertThrows(NestedServletException.class, () ->
                mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
        );
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
        assertThat(ex.getCause().getMessage()).isEqualTo("Persistence failure");
    }

    /**
     * TC-NOTIF-005: toCustomerId is a string instead of integer → 400 Bad Request.
     * Jackson cannot deserialize a string into Integer.
     */
    @Test
    @DisplayName("TC-NOTIF-005: String toCustomerId returns 400 Bad Request")
    void shouldReturnBadRequestWhenToCustomerIdIsNotAnInteger() throws Exception {
        String payload = """
                {
                    "toCustomerId": "not-a-number",
                    "toCustomerName": "alex.doe@example.com",
                    "message": "Hi Alex!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────── NEGATIVE CASES ───────────────────────────────

    /**
     * TC-NOTIF-006: No request body → 400 Bad Request.
     * Spring cannot bind a missing body to the required @RequestBody record.
     */
    @Test
    @DisplayName("TC-NOTIF-006: Missing request body returns 400 Bad Request")
    void shouldReturnBadRequestWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-NOTIF-007: Malformed JSON → 400 Bad Request.
     * Jackson throws HttpMessageNotReadableException for invalid syntax.
     */
    @Test
    @DisplayName("TC-NOTIF-007: Malformed JSON returns 400 Bad Request")
    void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
        String invalidPayload = """
                {
                    "toCustomerId": 1,
                    "toCustomerName": "alex.doe@example.com"
                """; // intentionally missing closing brace

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-NOTIF-008: Wrong Content-Type (text/plain) → 415 Unsupported Media Type.
     */
    @Test
    @DisplayName("TC-NOTIF-008: Wrong Content-Type returns 415 Unsupported Media Type")
    void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("toCustomerId=1"))
                .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * TC-NOTIF-009: No Content-Type header → 415 Unsupported Media Type.
     */
    @Test
    @DisplayName("TC-NOTIF-009: Missing Content-Type header returns 415 Unsupported Media Type")
    void shouldReturn415WhenContentTypeIsAbsent() throws Exception {
        String payload = """
                {
                    "toCustomerId": 1,
                    "toCustomerName": "alex.doe@example.com",
                    "message": "Hi Alex!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .content(payload))
                .andExpect(status().isUnsupportedMediaType());
    }
}
