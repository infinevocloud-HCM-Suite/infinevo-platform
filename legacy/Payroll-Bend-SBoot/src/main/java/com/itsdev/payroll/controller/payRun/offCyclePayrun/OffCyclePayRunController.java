package com.itsdev.payroll.controller.payRun.offCyclePayrun;


import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunDTO;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunImportRequestDTO;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.OffCyclePayRunReleaseWithheldImportRequestDTO;
import com.itsdev.payroll.service.payRun.offCyclePayrun.OffCyclePayRunService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/off-cycle-payruns")
public class OffCyclePayRunController {

    private final OffCyclePayRunService payRunService;

    public OffCyclePayRunController(OffCyclePayRunService payRunService) {
        this.payRunService = payRunService;
    }

    /**
     * Create a draft off-cycle pay run
     */
    @PostMapping("/draft")
    public ResponseEntity<OffCyclePayRunDTO> createDraftPayRun(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody OffCyclePayRunDTO requestDto) {
        return ResponseEntity.ok(payRunService.createPayRun(organizationId, requestDto));
    }

    /**
     * Update an existing off-cycle pay run
     */
    @PutMapping("/{payrollRunId}")
    public ResponseEntity<OffCyclePayRunDTO> updatePayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrollRunId,
            @Valid @RequestBody OffCyclePayRunDTO requestDto) {
        return ResponseEntity.ok(payRunService.updatePayRun(organizationId, payrollRunId, requestDto));
    }

    /**
     * Get a single off-cycle pay run by payrollRunId
     */
    @GetMapping("/{payrollRunId}")
    public ResponseEntity<OffCyclePayRunDTO> getPayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrollRunId) {
        return ResponseEntity.ok(payRunService.getPayRun(organizationId, payrollRunId));
    }

    /**
     * Get all off-cycle pay runs for an organization
     */
    @GetMapping
    public ResponseEntity<List<OffCyclePayRunDTO>> getAllPayRuns(
            @RequestHeader("organizationId") String organizationId) {
        return ResponseEntity.ok(payRunService.getAllPayRuns(organizationId));
    }

    /**
     * Delete an off-cycle pay run
     */
    @DeleteMapping("/{payrollRunId}")
    public ResponseEntity<Void> deletePayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrollRunId) {
        payRunService.deletePayRun(organizationId, payrollRunId);
        return ResponseEntity.noContent().build();
    }


    /**
     * Import employees into an existing off-cycle pay run
     */
    @PostMapping("/{payrollRunId}/import")
    public ResponseEntity<OffCyclePayRunDTO> importEmployeesToPayRun(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrollRunId,
            @Valid @RequestBody OffCyclePayRunImportRequestDTO requestDto) {
        OffCyclePayRunDTO response = payRunService.importEmployeesToPayRun(organizationId, payrollRunId, requestDto);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/{payrollRunId}/import/release-withheld-salary")
    public ResponseEntity<OffCyclePayRunDTO> importReleaseWithheldSalary(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrollRunId,
            @Valid @RequestBody OffCyclePayRunReleaseWithheldImportRequestDTO requestDto) {

        OffCyclePayRunDTO response = payRunService.importReleaseWithheldSalary(
                organizationId,
                payrollRunId,
                requestDto
        );
        return ResponseEntity.ok(response);
    }


}

