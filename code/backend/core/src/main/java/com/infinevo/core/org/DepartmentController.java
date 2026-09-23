package com.infinevo.core.org;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/departments} — the four endpoints of spec section 4 (W-14.1).
 *
 * <p>Thin by rule: unpack, delegate, repack — {@code docs/CONVENTIONS.md} section 3. Every decision
 * about what a department may be is in {@link DepartmentServiceImpl}; the error contract belongs to
 * {@link OrgMasterController}.
 *
 * <p><strong>No endpoint here names a tenant.</strong> No {@code /tenants/{id}/departments} path, no
 * {@code organizationId} header, no tenant field on the body. The tenant is bound by
 * {@code TenantContextFilter} from the verified token before the request reaches this class, and the
 * database enforces it again through row-level security.
 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController extends OrgMasterController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = Objects.requireNonNull(departmentService, "departmentService must not be null");
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@RequestBody DepartmentRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id()))
                .body(created);
    }

    /**
     * @param activeOnly true to leave out the departments an administrator has retired
     */
    @GetMapping
    public List<DepartmentResponse> list(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return departmentService.list(activeOnly);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable("id") UUID id, @RequestBody DepartmentRequest request) {
        return departmentService.update(id, request);
    }

    /** {@code 204}, and refused with {@code 409} while an employee is assigned — spec section 4. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
