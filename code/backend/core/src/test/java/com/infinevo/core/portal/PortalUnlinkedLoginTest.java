package com.infinevo.core.portal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.document.DocumentService;
import com.infinevo.core.document.MyDocumentController;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.MyEmployeeController;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-25 — a login with no linked employee record (an administrator, say) has no portal. The two
 * data endpoints must say so with a {@code 404}, never an unhandled exception.
 */
class PortalUnlinkedLoginTest {

    private final EmployeeService employeeService = mock(EmployeeService.class);

    @Test
    @DisplayName("GET /api/v1/me/employee is 404 for a login with no employee record")
    void myEmployeeIsNotFound() throws Exception {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MyEmployeeController(employeeService))
                .build();

        mvc.perform(get("/api/v1/me/employee")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/me/documents is 404 for a login with no employee record")
    void myDocumentsIsNotFound() throws Exception {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new MyDocumentController(mock(DocumentService.class), employeeService))
                .build();

        mvc.perform(get("/api/v1/me/documents")).andExpect(status().isNotFound());
    }
}
