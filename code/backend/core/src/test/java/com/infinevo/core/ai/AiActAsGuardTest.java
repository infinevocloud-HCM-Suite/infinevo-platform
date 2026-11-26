package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.impersonation.ActingAs;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** W-114.13b rule 1: every AI request is refused while acting as another user. */
class AiActAsGuardTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AiActAsGuard guard = new AiActAsGuard(json);

    @AfterEach
    void clear() {
        ActingAs.clear();
    }

    @Test
    @DisplayName("not acting as anyone: the request goes on")
    void notActing_passes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(guard.preHandle(new MockHttpServletRequest("GET", "/api/v1/ai/status"), response, null))
                .isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("acting as a user, or a bootstrap session: 403 AI_DISABLED with the act-as line, handler not run")
    void acting_refused() throws Exception {
        for (UUID target : new UUID[] {UUID.randomUUID(), null}) {
            ActingAs.set(UUID.randomUUID(), target, UUID.randomUUID(), null, Set.of("core.ai.use"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean goOn = guard.preHandle(
                    new MockHttpServletRequest("POST", "/api/v1/ai/sessions/x/messages"), response, null);

            assertThat(goOn).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            JsonNode body = json.readTree(response.getContentAsString());
            assertThat(body.path("code").asText()).isEqualTo("AI_DISABLED");
            assertThat(body.path("message").asText()).isEqualTo(AiActAsGuard.MESSAGE);
            ActingAs.clear();
        }
    }
}
