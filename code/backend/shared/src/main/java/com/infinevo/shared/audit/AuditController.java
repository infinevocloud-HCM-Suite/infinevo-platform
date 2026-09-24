package com.infinevo.shared.audit;

import com.infinevo.shared.audit.AuditQueryService.AuditLogView;
import com.infinevo.shared.authz.RequiresAction;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/audit} - the read side of the audit trail (W-22.1, spec section 4).
 *
 * <p>Thin by design: it unpacks the query string and delegates. The tenant is never a parameter;
 * it is bound by {@code TenantContextFilter} and read from {@code TenantContext} inside the
 * service.
 *
 * <p>Guarded by {@code core.audit.read} (W-11.2): the trail holds who changed what for every
 * employee, so it is for hr and the admins, not every authenticated user.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiresAction("core.audit.read")
public class AuditController {

    /** Rows per page when the caller does not say. */
    static final int DEFAULT_PAGE_SIZE = 50;

    /** The most rows one page will return, whatever is asked for. */
    static final int MAX_PAGE_SIZE = 200;

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Page<AuditLogView> search(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return queryService.search(entity, entityId, actor, from, to, pageRequest(page, size));
    }

    static PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
