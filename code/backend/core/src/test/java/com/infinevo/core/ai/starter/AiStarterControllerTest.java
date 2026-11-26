package com.infinevo.core.ai.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiStatus;
import com.infinevo.core.ai.starter.AiStarter.Kind;
import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** W-114.22: GET /api/v1/ai/starters - 403 AI_DISABLED when off; id, label, prompt and kind only. */
class AiStarterControllerTest {

    private final AiAccessGate gate = mock(AiAccessGate.class);
    private final AiStarterService service = mock(AiStarterService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiStarterController(gate, service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().registerModule(new JavaTimeModule())))
                .build();
    }

    @Test
    @DisplayName("helper off: 403 AI_DISABLED and the service is not asked")
    void off() throws Exception {
        when(gate.status()).thenReturn(new AiStatus(false, null, 0, "2026-11"));

        mvc.perform(get("/api/v1/ai/starters"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        verify(service, never()).forCaller();
    }

    @Test
    @DisplayName("on: each chip has id, label, prompt and kind, and no tool name, screen or roles")
    void on() throws Exception {
        when(gate.status()).thenReturn(new AiStatus(true, 100, 1, "2026-11"));
        when(service.forCaller())
                .thenReturn(List.of(new AiStarter(
                        "leave-left",
                        "How many leave days do I have left?",
                        "How many leave days do I have left?",
                        Kind.QUESTION,
                        "core.my_leave_balances",
                        "core.leave.types",
                        List.of("employee"))));

        String body = mvc.perform(get("/api/v1/ai/starters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("leave-left"))
                .andExpect(jsonPath("$[0].kind").value("QUESTION"))
                .andExpect(jsonPath("$[0].prompt").value("How many leave days do I have left?"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("core.my_leave_balances", "core.leave.types", "employee", "toolName");
    }

    @Test
    @DisplayName("guarded by core.ai.use")
    void guarded() throws Exception {
        RequiresAction required =
                AiStarterController.class.getMethod("starters").getAnnotation(RequiresAction.class);
        assertThat(required.value()).isEqualTo("core.ai.use");
    }
}
