package com.infinevo.core.org;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/work-locations} — the four endpoints of spec section 4 (W-14.1).
 *
 * <p>See {@link DepartmentController}. One handler is added that the other two masters cannot need:
 * a tenant has at most one filing address.
 */
@RestController
@RequestMapping("/api/v1/work-locations")
public class WorkLocationController extends OrgMasterController {

    private final WorkLocationService workLocationService;

    public WorkLocationController(WorkLocationService workLocationService) {
        this.workLocationService = Objects.requireNonNull(workLocationService, "workLocationService must not be null");
    }

    @PostMapping
    @RequiresAction("core.org.manage")
    public ResponseEntity<WorkLocationResponse> create(@RequestBody WorkLocationRequest request) {
        WorkLocationResponse created = workLocationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/work-locations/" + created.id()))
                .body(created);
    }

    /**
     * @param activeOnly true to leave out the work locations an administrator has retired
     */
    @GetMapping
    @RequiresAction("core.org.read")
    public List<WorkLocationResponse> list(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return workLocationService.list(activeOnly);
    }

    @PutMapping("/{id}")
    @RequiresAction("core.org.manage")
    public WorkLocationResponse update(@PathVariable("id") UUID id, @RequestBody WorkLocationRequest request) {
        return workLocationService.update(id, request);
    }

    /** {@code 204}, and refused with {@code 409} while an employee is assigned — spec section 4. */
    @DeleteMapping("/{id}")
    @RequiresAction("core.org.manage")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        workLocationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code 409} on a second filing address. The flag is not moved implicitly — see
     * {@link WorkLocationService.FilingAddressAlreadySetException} for why.
     */
    @ExceptionHandler(WorkLocationService.FilingAddressAlreadySetException.class)
    public ResponseEntity<ApiErrorResponse> handleFilingAddress(
            WorkLocationService.FilingAddressAlreadySetException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }
}
