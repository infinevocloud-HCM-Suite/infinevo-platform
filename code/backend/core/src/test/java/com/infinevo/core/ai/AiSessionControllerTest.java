package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.RequiresAction;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * W-114.3 spec, Tests: {@link AiSessionController} error answers and its guard. Standalone MockMvc does not run
 * {@code @RequiresAction}; that each endpoint carries {@code core.ai.use} is read from the annotations, and
 * {@code AiSessionIT} exercises the real check over HTTP.
 */
class AiSessionControllerTest {

    private AiSessionService service;
    private AiAccessGate gate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AiSessionService.class);
        gate = mock(AiAccessGate.class);
        when(gate.status()).thenReturn(new AiStatus(true, 100, 0, "2026-11"));
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new AiSessionController(service, gate))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("every endpoint requires core.ai.use")
    void everyEndpoint_requiresCoreAiUse() {
        List<Method> endpoints = Arrays.stream(AiSessionController.class.getDeclaredMethods())
                .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
                .toList();
        assertThat(endpoints).hasSize(4);
        for (Method endpoint : endpoints) {
            RequiresAction guard = endpoint.getAnnotation(RequiresAction.class);
            assertThat(guard).as(endpoint.getName()).isNotNull();
            assertThat(guard.value()).as(endpoint.getName()).isEqualTo("core.ai.use");
            assertThat(guard.anyOf()).as(endpoint.getName()).isEmpty();
        }
    }

    @Test
    @DisplayName("POST with the switch on: 201 with id, title, createdAt, lastMessageAt")
    void create_201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create("Leave")).thenReturn(new AiSessionResponse(id, "Leave", Instant.now(), null));

        mvc.perform(post("/api/v1/ai/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Leave\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Leave"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.lastMessageAt").doesNotExist());
    }

    @Test
    @DisplayName("POST with no body: 201, no title")
    void create_noBody() throws Exception {
        when(service.create(null)).thenReturn(new AiSessionResponse(UUID.randomUUID(), null, Instant.now(), null));

        mvc.perform(post("/api/v1/ai/sessions")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST with the customer's switch off: 403 AI_DISABLED and nothing is created")
    void create_switchOff_403AiDisabled() throws Exception {
        when(gate.status()).thenReturn(new AiStatus(false, null, 0, "2026-11"));

        mvc.perform(post("/api/v1/ai/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Leave\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("POST with a title the service refuses: 400 VALIDATION_FAILED")
    void create_badTitle_400() throws Exception {
        when(service.create(anyString())).thenThrow(new IllegalArgumentException("title must be at most 200"));

        mvc.perform(post("/api/v1/ai/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET messages of an unknown or foreign id: 404 NOT_FOUND")
    void messages_unknown_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.messages(id)).thenThrow(new AiSessionNotFoundException(id));

        mvc.perform(get("/api/v1/ai/sessions/{id}/messages", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE of an unknown id: 404; of a known one: 204")
    void delete_unknown_404_known_204() throws Exception {
        UUID unknown = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AiSessionNotFoundException(unknown))
                .when(service)
                .delete(unknown);

        mvc.perform(delete("/api/v1/ai/sessions/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(delete("/api/v1/ai/sessions/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("an id that is not a UUID: 400 VALIDATION_FAILED")
    void badId_400() throws Exception {
        mvc.perform(get("/api/v1/ai/sessions/not-a-uuid/messages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET list: size defaults to 20 and is held to 50; a negative page or size 0 is 400")
    void list_pageSize() throws Exception {
        when(service.listMine(any())).thenAnswer(inv -> new PageImpl<>(List.of(), inv.<Pageable>getArgument(0), 0));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        mvc.perform(get("/api/v1/ai/sessions")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai/sessions").param("page", "2").param("size", "500"))
                .andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).listMine(pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(20);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(50);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isEqualTo(2);

        mvc.perform(get("/api/v1/ai/sessions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/ai/sessions").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
