package com.infinevo.payroll.payrun.bankfile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-88.2 spec section 4.4 — each endpoint behind the real {@link RequiresActionAspect}: reading templates and
 * making files need {@code payroll.bank_file.export}; changing a template needs {@code payroll.settings.manage}.
 */
class BankFileGuardIT {

    private static final String EXPORT = "payroll.bank_file.export";
    private static final String MANAGE = "payroll.settings.manage";
    private static final String BODY = "{\"name\":\"X\",\"columns\":[{\"heading\":\"#\",\"source\":\"SERIAL\"}]}";

    private BankFileTemplateService templateService;
    private BankFileExportService exportService;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        templateService = mock(BankFileTemplateService.class);
        exportService = mock(BankFileExportService.class);
        permissionService = mock(PermissionService.class);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());
        mvc = MockMvcBuilders.standaloneSetup(
                        proxy(new BankFileTemplateController(templateService)),
                        proxy(new BankFileExportController(exportService)))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    private <T> T proxy(T controller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return factory.getProxy();
    }

    @Test
    @DisplayName("without payroll.bank_file.export every read and every file endpoint is 403")
    void exportActionGuardsReadsAndFiles() throws Exception {
        given(permissionService.holds(EXPORT)).willReturn(false);
        UUID run = UUID.randomUUID();
        mvc.perform(get("/api/v1/payroll/bank-file-templates")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/bank-file-templates/fields")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/bank-file-templates/generic-neft")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/bank-files/preview", run)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"generic-neft\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/bank-files", run)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"generic-neft\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/payruns/{id}/bank-files", run)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/payruns/{id}/bank-files/{e}/download", run, UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(exportService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("payroll.bank_file.export alone cannot change a template; payroll.settings.manage can")
    void manageActionGuardsWrites() throws Exception {
        given(permissionService.holds(EXPORT)).willReturn(true);
        given(permissionService.holds(MANAGE)).willReturn(false);
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/payroll/bank-file-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/payroll/bank-file-templates/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/payroll/bank-file-templates/{id}", id)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payroll/bank-file-templates/{id}/copy", id)).andExpect(status().isForbidden());
        verify(templateService, never()).create(any());

        given(permissionService.holds(MANAGE)).willReturn(true);
        mvc.perform(delete("/api/v1/payroll/bank-file-templates/{id}", id)).andExpect(status().isNoContent());
        verify(templateService).delete(id);
    }

    @Test
    @DisplayName("errors map: not found 404, built-in change 409, bad template 400")
    void errorMapping() throws Exception {
        given(permissionService.holds(any())).willReturn(true);
        given(templateService.get("nope"))
                .willThrow(new BankFileTemplateException(BankFileTemplateException.Kind.NOT_FOUND, "No template nope"));
        mvc.perform(get("/api/v1/payroll/bank-file-templates/nope")).andExpect(status().isNotFound());

        given(templateService.update(any(), any()))
                .willThrow(new BankFileTemplateException(BankFileTemplateException.Kind.CONFLICT, "built-in"));
        mvc.perform(put("/api/v1/payroll/bank-file-templates/generic-neft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());

        given(templateService.create(any())).willThrow(new IllegalArgumentException("name is required"));
        mvc.perform(post("/api/v1/payroll/bank-file-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }
}
