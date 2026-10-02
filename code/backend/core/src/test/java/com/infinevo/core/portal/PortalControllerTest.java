package com.infinevo.core.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.MyEmployeeController;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class PortalControllerTest {

    @Test
    @DisplayName("PortalController delegates to PortalPanelService")
    void portalControllerDelegates() {
        PortalPanelService service = mock(PortalPanelService.class);
        PanelDescriptor descriptor =
                new PanelDescriptor("profile", "My Profile", 1, "/api/v1/me/employee", "core.employee.read_own");
        when(service.getPanels()).thenReturn(List.of(descriptor));

        PortalController controller = new PortalController(service);
        ResponseEntity<List<PanelDescriptor>> response = controller.getPanels();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsExactly(descriptor);
    }

    @Test
    @DisplayName("MyEmployeeController returns current employee when present")
    void myEmployeeControllerReturnsCurrent() {
        EmployeeService employeeService = mock(EmployeeService.class);
        EmployeeResponse employee = new EmployeeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EMP-001",
                "Ravi",
                null,
                "Kumar",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "ravi@test.local",
                "9876543210",
                true,
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        when(employeeService.currentEmployee()).thenReturn(Optional.of(employee));

        MyEmployeeController controller = new MyEmployeeController(employeeService);
        ResponseEntity<EmployeeResponse> response = controller.getMyEmployee();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(employee);
    }
}
