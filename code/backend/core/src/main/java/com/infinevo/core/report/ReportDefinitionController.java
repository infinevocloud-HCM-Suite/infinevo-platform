package com.infinevo.core.report;

import com.infinevo.shared.authz.RequiresAction;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/report-definitions} (W-23.1, spec section 4).
 *
 * <p>{@code POST} creates and {@code PUT} updates — decision D6, the shape every other platform
 * resource takes ({@code RoleController}, {@code DepartmentController}). The spec listed only
 * {@code PUT /{id}}, which would have left no way to create a tenant's own definition.
 */
@RestController
@RequestMapping("/api/v1/report-definitions")
public class ReportDefinitionController extends ReportController {

    private final ReportDefinitionService service;

    public ReportDefinitionController(ReportDefinitionService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /** The tenant's definitions the caller may run; one whose required action they lack is not listed. */
    @GetMapping
    @RequiresAction("core.report.read")
    public List<ReportDefinitionResponse> list() {
        return service.list();
    }

    @PostMapping
    @RequiresAction("core.report.manage")
    public ResponseEntity<ReportDefinitionResponse> create(@RequestBody ReportDefinitionRequest request) {
        ReportDefinitionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/report-definitions/" + created.id()))
                .body(created);
    }

    /** {@code 200}; {@code 409} for a system definition. */
    @PutMapping("/{id}")
    @RequiresAction("core.report.manage")
    public ReportDefinitionResponse update(@PathVariable("id") UUID id, @RequestBody ReportDefinitionRequest request) {
        return service.update(id, request);
    }
}
