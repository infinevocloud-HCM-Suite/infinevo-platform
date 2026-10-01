package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller serving the authenticated caller's own documents (W-25, spec section 4).
 *
 * <p>Mounted at {@code /api/v1/me/documents} and gated by {@code core.document.read_own}.
 */
@RestController
@RequestMapping("/api/v1/me/documents")
public class MyDocumentController {

    private final DocumentService documentService;
    private final EmployeeService employeeService;

    public MyDocumentController(DocumentService documentService, EmployeeService employeeService) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @RequiresAction("core.document.read_own")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments() {
        return employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .map(documentService::findByEmployee)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EmployeeService.NotFoundException("No employee profile linked to current user"));
    }
}
