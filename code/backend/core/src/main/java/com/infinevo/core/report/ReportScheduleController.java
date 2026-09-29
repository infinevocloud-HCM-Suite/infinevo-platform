package com.infinevo.core.report;

import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/report-schedules} (W-23.2, spec section 4) — in {@code core}, so {@code app} serves
 * it: {@code worker} exposes nothing but its health probe. Thin by rule; the rules are
 * {@link ReportScheduleServiceImpl}'s.
 */
@RestController
@RequestMapping("/api/v1/report-schedules")
@RequiresAction("core.report_schedule.manage")
public class ReportScheduleController extends ReportController {

    private final ReportScheduleService schedules;

    public ReportScheduleController(ReportScheduleService schedules) {
        this.schedules = Objects.requireNonNull(schedules, "schedules must not be null");
    }

    /** The tenant's schedules, each with how its last run went. */
    @GetMapping
    public List<ReportScheduleService.ReportScheduleResponse> list() {
        return schedules.list();
    }

    /**
     * Creates the schedule under {@code id} or replaces it — {@code 200} either way. The caller also
     * needs the definition's {@code required_action}.
     */
    @PutMapping("/{id}")
    public ReportScheduleService.ReportScheduleResponse put(
            @PathVariable("id") UUID id, @RequestBody ReportScheduleService.ReportScheduleRequest request) {
        return schedules.put(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        schedules.delete(id);
        return ResponseEntity.noContent().build();
    }
}
