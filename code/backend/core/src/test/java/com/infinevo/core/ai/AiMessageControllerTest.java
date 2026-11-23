package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.RequiresAction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** W-114.4 API contract: {@link AiMessageController}'s answers and error mapping. The guard is read from the code. */
class AiMessageControllerTest {

    private final UUID sessionId = UUID.randomUUID();
    private AiChatService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AiChatService.class);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new AiMessageController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("200 { message, proposedAction: null }; any context is accepted and ignored")
    void answers() throws Exception {
        UUID messageId = UUID.randomUUID();
        when(service.send(eq(sessionId), eq("How much leave?")))
                .thenReturn(new AiChatResponse(
                        new AiChatResponse.Message(
                                messageId, AiMessageRole.ASSISTANT, "12 days.", Instant.parse("2026-11-23T10:00:00Z")),
                        null));

        for (String context : new String[] {"{\"path\":\"/leave\"}", "\"just text\"", "[1,2]", "null", "42"}) {
            mvc.perform(post("/api/v1/ai/sessions/{id}/messages", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"How much leave?\",\"context\":" + context + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message.id").value(messageId.toString()))
                    .andExpect(jsonPath("$.message.role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.message.content").value("12 days."))
                    .andExpect(jsonPath("$.message.createdAt").exists())
                    .andExpect(jsonPath("$.proposedAction").isEmpty());
        }
    }

    @Test
    @DisplayName("a model failure is 502 INTERNAL with the fixed message, not the cause")
    void modelFailure() throws Exception {
        when(service.send(any(), any())).thenThrow(new AiModelException("The model answered HTTP 500"));

        mvc.perform(post("/api/v1/ai/sessions/{id}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value(AiMessageController.MODEL_FAILED));
    }

    @Test
    @DisplayName("403 AI_DISABLED, 429 AI_CAP_REACHED, 404, and 400 for bad content or a bad id")
    void errors() throws Exception {
        when(service.send(any(), eq("off"))).thenThrow(new AiDisabledException());
        when(service.send(any(), eq("cap"))).thenThrow(new AiCapReachedException(100));
        when(service.send(any(), eq("gone"))).thenThrow(new AiSessionNotFoundException(sessionId));
        when(service.send(any(), eq(""))).thenThrow(new IllegalArgumentException("content must not be empty"));

        mvc.perform(body("off"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        mvc.perform(body("cap"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_CAP_REACHED"));
        mvc.perform(body("gone")).andExpect(status().isNotFound());
        mvc.perform(body("")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/sessions/not-a-uuid/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the endpoint needs core.ai.use")
    void guarded() throws Exception {
        RequiresAction guard = AnnotatedElementUtils.findMergedAnnotation(
                AiMessageController.class.getMethod("send", UUID.class, AiMessageRequest.class), RequiresAction.class);
        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("core.ai.use");
    }

    private org.springframework.test.web.servlet.RequestBuilder body(String content) {
        return post("/api/v1/ai/sessions/{id}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}");
    }
}
