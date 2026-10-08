package com.infinevo.core.employeeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * W-73.3's rule, applied to the file (W-73.7): a dry run or an import in which any row grants a role
 * beyond {@code employee} needs {@code core.role.assign}; refused before the service is reached.
 */
class EmployeeImportControllerTest {

    private static final String HEADER = String.join(",", EmployeeImportParser.COLUMNS) + "\n";
    private static final String WITH_ROLES = HEADER + "E1,Asha,,asha@x.test,,2026-04-01,,,,Y,hr\n";
    private static final String EMPLOYEE_ONLY = HEADER + "E1,Asha,,asha@x.test,,2026-04-01,,,,Y,employee\n";

    private final EmployeeImportService service = mock(EmployeeImportService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final EmployeeImportController controller = new EmployeeImportController(service, permissions);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("dry run of a file granting roles, without core.role.assign: 403 and the service is not called")
    void dryRunNeedsRoleAssign() {
        doThrow(new PermissionDeniedException("core.role.assign"))
                .when(permissions)
                .require("core.role.assign");

        assertThatThrownBy(() -> controller.dryRun(file(WITH_ROLES))).isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("import of a file granting roles, without core.role.assign: 403 and nothing is queued")
    void importNeedsRoleAssign() {
        doThrow(new PermissionDeniedException("core.role.assign"))
                .when(permissions)
                .require("core.role.assign");

        assertThatThrownBy(() -> controller.importFile(file(WITH_ROLES), true))
                .isInstanceOf(PermissionDeniedException.class);
        verify(service, never()).enqueueImport(anyString(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("a file with no roles, or employee only, needs only core.employee.create")
    void noRolesNeedsNothingMore() {
        when(service.dryRun(any())).thenReturn(List.of());

        controller.dryRun(file(EMPLOYEE_ONLY));

        verify(permissions, never()).require(anyString());
        verify(service).dryRun(any());
    }

    @Test
    @DisplayName("with core.role.assign the import is queued as the token's subject: 202 with the job id")
    void queuedAsTheCaller() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(subject.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(service.enqueueImport(anyString(), any(), anyBoolean(), any())).thenReturn("job-1");

        var response = controller.importFile(file(WITH_ROLES), false);

        verify(permissions).require("core.role.assign");
        verify(service).enqueueImport(anyString(), any(), anyBoolean(), org.mockito.ArgumentMatchers.eq(subject));
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("jobId", "job-1");
    }

    @Test
    @DisplayName("an upload that is missing, too large or not UTF-8 is refused as a file error")
    void uploadRefusals() {
        assertThatThrownBy(() -> EmployeeImportController.read(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(EmployeeImportFileException.class);
        assertThatThrownBy(() -> EmployeeImportController.read(
                        new MockMultipartFile("file", new byte[(int) EmployeeImportController.MAX_FILE_BYTES + 1])))
                .hasMessageContaining("larger than 1 MB");
        assertThatThrownBy(() -> EmployeeImportController.read(
                        new MockMultipartFile("file", new byte[] {(byte) 0xC3, (byte) 0x28})))
                .hasMessageContaining("not UTF-8");
    }

    private static MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "employees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
