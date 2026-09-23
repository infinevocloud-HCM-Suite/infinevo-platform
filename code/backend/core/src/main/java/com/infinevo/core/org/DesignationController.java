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
 * {@code /api/v1/designations} — the four endpoints of spec section 4 (W-14.1).
 *
 * <p>See {@link DepartmentController}: the same four endpoints and the same tenancy rule over a
 * different master.
 */
@RestController
@RequestMapping("/api/v1/designations")
public class DesignationController extends OrgMasterController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = Objects.requireNonNull(designationService, "designationService must not be null");
    }

    @PostMapping
    public ResponseEntity<DesignationResponse> create(@RequestBody DesignationRequest request) {
        DesignationResponse created = designationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/designations/" + created.id()))
                .body(created);
    }

    /**
     * @param activeOnly true to leave out the designations an administrator has retired
     */
    @GetMapping
    public List<DesignationResponse> list(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return designationService.list(activeOnly);
    }

    @PutMapping("/{id}")
    public DesignationResponse update(@PathVariable("id") UUID id, @RequestBody DesignationRequest request) {
        return designationService.update(id, request);
    }

    /** {@code 204}, and refused with {@code 409} while an employee is assigned — spec section 4. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        designationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
