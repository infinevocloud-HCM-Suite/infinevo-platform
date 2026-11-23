package com.infinevo.core.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.authz.RequiresAction;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantOptionControllerTest {

    private MockMvc mvc;
    private TenantOptionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(TenantOptionService.class);
        TenantOptionController controller = new TenantOptionController(service);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/tenant-options lists options")
    void listOptions() throws Exception {
        TenantOptionResponse resp = new TenantOptionResponse(
                "core.example.switch",
                OptionCategory.GENERAL,
                OptionValueType.BOOLEAN,
                "Sample",
                "Description",
                "false",
                "false",
                false);
        when(service.list(null)).thenReturn(List.of(resp));

        mvc.perform(get("/api/v1/tenant-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("core.example.switch"))
                .andExpect(jsonPath("$[0].value").value("false"));
    }

    @Test
    @DisplayName("PUT with unknown key returns 404 NOT_FOUND")
    void putUnknownKeyReturns404() throws Exception {
        when(service.set(eq("no.such.key"), any()))
                .thenThrow(new TenantOptionService.NoSuchOptionException("no.such.key"));

        mvc.perform(put("/api/v1/tenant-options/no.such.key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TenantOptionUpdateRequest("true"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown tenant option key: no.such.key"));
    }

    @Test
    @DisplayName("PUT with bad value returns 400 VALIDATION_FAILED")
    void putBadValueReturns400() throws Exception {
        when(service.set(eq("core.example.switch"), eq("maybe")))
                .thenThrow(new TenantOptionService.OptionValidationException("Value must be 'true' or 'false'"));

        mvc.perform(put("/api/v1/tenant-options/core.example.switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TenantOptionUpdateRequest("maybe"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Value must be 'true' or 'false'"));
    }

    @Test
    @DisplayName("DELETE resets option to default")
    void deleteResetsOption() throws Exception {
        TenantOptionResponse resp = new TenantOptionResponse(
                "core.example.switch",
                OptionCategory.GENERAL,
                OptionValueType.BOOLEAN,
                "Sample",
                "Description",
                "false",
                "false",
                false);
        when(service.reset("core.example.switch")).thenReturn(resp);

        mvc.perform(delete("/api/v1/tenant-options/core.example.switch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("false"))
                .andExpect(jsonPath("$.overridden").value(false));

        verify(service).reset("core.example.switch");
    }

    @Test
    @DisplayName("Endpoints have correct @RequiresAction: manage needed to write, read alone cannot write")
    void actionAnnotationsEnforced() throws NoSuchMethodException {
        Method listMethod = TenantOptionController.class.getMethod("list", OptionCategory.class);
        RequiresAction listAction = listMethod.getAnnotation(RequiresAction.class);
        assertThat(listAction).isNotNull();
        assertThat(listAction.value()).isEqualTo("core.tenant_option.read");

        Method setMethod = TenantOptionController.class.getMethod("set", String.class, TenantOptionUpdateRequest.class);
        RequiresAction setAction = setMethod.getAnnotation(RequiresAction.class);
        assertThat(setAction).isNotNull();
        assertThat(setAction.value()).isEqualTo("core.tenant_option.manage");

        Method resetMethod = TenantOptionController.class.getMethod("reset", String.class);
        RequiresAction resetAction = resetMethod.getAnnotation(RequiresAction.class);
        assertThat(resetAction).isNotNull();
        assertThat(resetAction.value()).isEqualTo("core.tenant_option.manage");
    }
}
