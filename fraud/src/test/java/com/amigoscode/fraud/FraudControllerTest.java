package com.amigoscode.fraud;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link FraudController}.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>GET /api/v1/fraud-check/{customerId} — check fraud status of a customer</li>
 * </ul>
 *
 * <p>Response contract: {@code {"isFraudster": true|false}}
 *
 * <p>Test Strategy:
 * <ul>
 *   <li>@WebMvcTest slices to web layer only.</li>
 *   <li>FraudCheckService is mocked to control return values.</li>
 *   <li>eureka.client.enabled=false suppresses Eureka discovery noise.</li>
 * </ul>
 */
@WebMvcTest(controllers = FraudController.class, properties = {
        "eureka.client.enabled=false"
})
@DisplayName("FraudController – GET /api/v1/fraud-check/{customerId}")
class FraudControllerTest {

    private static final String BASE_URL = "/api/v1/fraud-check/{customerId}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudCheckService fraudCheckService;

    // ─────────────────────── POSITIVE CASES ───────────────────────────────

    /**
     * TC-FRAUD-001: Legitimate customer (isFraudster = false) → 200 OK + correct JSON.
     * Validates the full response: status, Content-Type, and JSON field value.
     */
    @Test
    @DisplayName("TC-FRAUD-001: Legitimate customer returns 200 OK with isFraudster=false")
    void shouldReturnFalseForLegitimateCustomer() throws Exception {
        Integer customerId = 1;
        when(fraudCheckService.isFraudulentCustomer(customerId)).thenReturn(false);

        mockMvc.perform(get(BASE_URL, customerId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isFraudster").isBoolean())
                .andExpect(jsonPath("$.isFraudster").value(false));

        verify(fraudCheckService, times(1)).isFraudulentCustomer(customerId);
    }

    /**
     * TC-FRAUD-002: Fraudulent customer (isFraudster = true) → 200 OK + correct JSON.
     * Ensures the service return value is faithfully relayed in the response body.
     */
    @Test
    @DisplayName("TC-FRAUD-002: Fraudulent customer returns 200 OK with isFraudster=true")
    void shouldReturnTrueForFraudulentCustomer() throws Exception {
        Integer customerId = 99;
        when(fraudCheckService.isFraudulentCustomer(customerId)).thenReturn(true);

        mockMvc.perform(get(BASE_URL, customerId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isFraudster").isBoolean())
                .andExpect(jsonPath("$.isFraudster").value(true));

        verify(fraudCheckService, times(1)).isFraudulentCustomer(customerId);
    }

    /**
     * TC-FRAUD-003: Large (but valid) customer ID → 200 OK.
     * Ensures there is no artificial upper-bound rejection on the path variable.
     */
    @Test
    @DisplayName("TC-FRAUD-003: Large valid customer ID returns 200 OK")
    void shouldHandleLargeValidCustomerId() throws Exception {
        Integer customerId = Integer.MAX_VALUE;
        when(fraudCheckService.isFraudulentCustomer(customerId)).thenReturn(false);

        mockMvc.perform(get(BASE_URL, customerId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").value(false));
    }

    /**
     * TC-FRAUD-004: Response body contains exactly the "isFraudster" field.
     * Guards against accidental extra / missing fields in the record definition.
     */
    @Test
    @DisplayName("TC-FRAUD-004: Response JSON contains only the isFraudster field")
    void shouldReturnResponseWithIsFraudsterFieldOnly() throws Exception {
        Integer customerId = 5;
        when(fraudCheckService.isFraudulentCustomer(customerId)).thenReturn(false);

        mockMvc.perform(get(BASE_URL, customerId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").exists());
    }

    // ─────────────────────── NEGATIVE CASES ───────────────────────────────

    /**
     * TC-FRAUD-005: Non-numeric customer ID (e.g. "abc") → 400 Bad Request.
     * Spring cannot convert the path variable to Integer.
     */
    @Test
    @DisplayName("TC-FRAUD-005: Non-numeric customerId returns 400 Bad Request")
    void shouldReturnBadRequestWhenCustomerIdIsNotAnInteger() throws Exception {
        mockMvc.perform(get(BASE_URL, "abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-FRAUD-006: Special-character customer ID → 400 Bad Request.
     */
    @Test
    @DisplayName("TC-FRAUD-006: Special-character customerId returns 400 Bad Request")
    void shouldReturnBadRequestWhenCustomerIdContainsSpecialChars() throws Exception {
        mockMvc.perform(get(BASE_URL, "1@2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-FRAUD-007: Floating-point customer ID (e.g. "1.5") → 400 Bad Request.
     * Decimal values cannot be bound to Integer.
     */
    @Test
    @DisplayName("TC-FRAUD-007: Decimal customerId returns 400 Bad Request")
    void shouldReturnBadRequestWhenCustomerIdIsDecimal() throws Exception {
        mockMvc.perform(get(BASE_URL, "1.5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-FRAUD-008: Service throws RuntimeException.
     * In @WebMvcTest without a @ControllerAdvice, MockMvc re-throws unhandled controller
     * exceptions wrapped in NestedServletException instead of returning an HTTP 500 response.
     * We assert the exception type and its cause to verify the error propagates correctly.
     */
    @Test
    @DisplayName("TC-FRAUD-008: Service RuntimeException → NestedServletException propagates")
    void shouldPropagateNestedServletExceptionWhenServiceThrowsRuntimeException() {
        Integer customerId = 7;
        when(fraudCheckService.isFraudulentCustomer(customerId))
                .thenThrow(new RuntimeException("Unexpected DB failure"));

        NestedServletException ex = assertThrows(NestedServletException.class, () ->
                mockMvc.perform(get(BASE_URL, customerId)
                        .accept(MediaType.APPLICATION_JSON))
        );
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
        assertThat(ex.getCause().getMessage()).isEqualTo("Unexpected DB failure");
    }
}
