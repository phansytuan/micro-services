package com.amigoscode.customer;

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
 * Unit tests for {@link CustomerController}.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>POST /api/v1/customers — register a new customer</li>
 * </ul>
 *
 * <p>Test Strategy:
 * <ul>
 *   <li>@WebMvcTest slices the Spring context to the web layer only.</li>
 *   <li>CustomerService is mocked so tests are fast and isolated.</li>
 *   <li>eureka.client.enabled=false prevents Eureka auto-registration noise.</li>
 * </ul>
 */
@WebMvcTest(controllers = CustomerController.class, properties = {
        "eureka.client.enabled=false"
})
@DisplayName("CustomerController – POST /api/v1/customers")
class CustomerControllerTest {

    private static final String BASE_URL = "/api/v1/customers";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    // ─────────────────────── POSITIVE CASES ───────────────────────────────

    /**
     * TC-CUST-001: Valid payload → 200 OK
     * Verifies that a well-formed registration request is accepted and
     * delegated to CustomerService exactly once.
     */
    @Test
    @DisplayName("TC-CUST-001: Valid payload returns 200 OK and delegates to service")
    void shouldRegisterCustomerSuccessfully() throws Exception {
        doNothing().when(customerService).registerCustomer(any());

        String payload = """
                {
                    "firstName": "Alex",
                    "lastName": "Doe",
                    "email": "alex.doe@example.com"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(customerService, times(1)).registerCustomer(any(CustomerRegistrationRequest.class));
    }

    /**
     * TC-CUST-002: Partial payload (firstName only) → 200 OK
     * The controller has no @Valid annotation, so partial payloads are
     * still accepted (null fields are passed to the service).
     */
    @Test
    @DisplayName("TC-CUST-002: Partial payload (no validation) returns 200 OK")
    void shouldAcceptPartialPayloadBecauseNoValidationAnnotation() throws Exception {
        doNothing().when(customerService).registerCustomer(any());

        String payload = """
                {
                    "firstName": "Alex"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    /**
     * TC-CUST-003: Empty JSON object {} → 200 OK
     * All record fields are null, but no validation is enforced by the controller.
     */
    @Test
    @DisplayName("TC-CUST-003: Empty JSON object {} is accepted (no validation) — 200 OK")
    void shouldAcceptEmptyJsonObjectWithoutValidation() throws Exception {
        doNothing().when(customerService).registerCustomer(any());

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    /**
     * TC-CUST-004: Service throws IllegalStateException (e.g. fraudster detected).
     * In @WebMvcTest without a @ControllerAdvice, MockMvc re-throws unhandled controller
     * exceptions wrapped in NestedServletException instead of returning an HTTP 500 response.
     * We assert the exception type and its cause to verify the error propagates correctly.
     */
    @Test
    @DisplayName("TC-CUST-004: Service throws IllegalStateException → NestedServletException propagates")
    void shouldPropagateNestedServletExceptionWhenServiceThrowsIllegalStateException() {
        doThrow(new IllegalStateException("fraudster"))
                .when(customerService).registerCustomer(any());

        String payload = """
                {
                    "firstName": "Bad",
                    "lastName": "Actor",
                    "email": "fraudster@evil.com"
                }
                """;

        NestedServletException ex = assertThrows(NestedServletException.class, () ->
                mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
        );
        assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(ex.getCause().getMessage()).isEqualTo("fraudster");
    }

    // ─────────────────────── NEGATIVE CASES ───────────────────────────────

    /**
     * TC-CUST-005: Missing request body → 400 Bad Request
     * Spring cannot deserialize a missing body into CustomerRegistrationRequest.
     */
    @Test
    @DisplayName("TC-CUST-005: Missing request body returns 400 Bad Request")
    void shouldReturnBadRequestWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-CUST-006: Malformed JSON → 400 Bad Request
     * Spring's message converter rejects a payload that cannot be parsed.
     */
    @Test
    @DisplayName("TC-CUST-006: Malformed JSON returns 400 Bad Request")
    void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
        String invalidPayload = """
                {
                    "firstName": "Alex",
                    "lastName": "Doe",
                    "email": "alex.doe@example.com"
                """; // intentionally missing closing brace

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-CUST-007: Wrong Content-Type (text/plain) → 415 Unsupported Media Type
     * The endpoint only accepts application/json.
     */
    @Test
    @DisplayName("TC-CUST-007: Wrong Content-Type returns 415 Unsupported Media Type")
    void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("firstName=Alex"))
                .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * TC-CUST-008: No Content-Type header → 415 Unsupported Media Type
     */
    @Test
    @DisplayName("TC-CUST-008: Missing Content-Type header returns 415 Unsupported Media Type")
    void shouldReturn415WhenContentTypeIsAbsent() throws Exception {
        String payload = """
                {
                    "firstName": "Alex",
                    "lastName": "Doe",
                    "email": "alex.doe@example.com"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .content(payload))
                .andExpect(status().isUnsupportedMediaType());
    }
}
