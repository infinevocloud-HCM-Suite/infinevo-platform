package com.infinevo.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Propagates existing X-Correlation-ID header into MDC and response header")
    void shouldPropagateExistingCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcCorrelationIdDuringChain = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcCorrelationIdDuringChain.set(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, filterChain);

        assertThat(mdcCorrelationIdDuringChain.get()).isEqualTo("corr-abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("corr-abc-123");
        assertThat(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Generates new UUID when X-Correlation-ID header is absent")
    void shouldGenerateCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcCorrelationIdDuringChain = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcCorrelationIdDuringChain.set(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, filterChain);

        assertThat(mdcCorrelationIdDuringChain.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(mdcCorrelationIdDuringChain.get());
        assertThat(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Cleans MDC correlation ID even if filter chain throws exception")
    void shouldCleanMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            throw new RuntimeException("Simulated error");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated error");

        assertThat(MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)).isNull();
    }
}
