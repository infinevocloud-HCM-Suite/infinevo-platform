package com.infinevo.payroll.form16;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller exposing Form 16 Part A endpoints (W-36.5 §4):
 * TRACES ZIP upload, officer certificate list, and employee self-service signed link download.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class Form16PartAController {

    private static final Logger log = LoggerFactory.getLogger(Form16PartAController.class);

    private final Form16PartAService form16PartAService;

    public Form16PartAController(Form16PartAService form16PartAService) {
        this.form16PartAService = Objects.requireNonNull(form16PartAService, "form16PartAService must not be null");
    }

    /**
     * Officer endpoint to upload the TRACES Form 16 Part A ZIP for a financial year.
     */
    @PostMapping(value = "/api/v1/payroll/form16/{fy}/part-a", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAction("payroll.statutory_report.generate")
    public PartAResponse<PartAUploadResult> upload(
            @PathVariable("fy") String financialYear, @RequestParam("file") MultipartFile file) {
        log.info("Processing Form 16 Part A ZIP upload for financial year {}", financialYear);
        PartAUploadResult result = form16PartAService.upload(financialYear, file);
        return PartAResponse.ok("Form 16 Part A upload processed successfully", result);
    }

    /**
     * Officer endpoint to list active Form 16 Part A records for a financial year.
     */
    @GetMapping("/api/v1/payroll/form16/{fy}/part-a")
    @RequiresAction("payroll.statutory_report.read")
    public PartAResponse<List<PartAOfficerItem>> list(@PathVariable("fy") String financialYear) {
        log.info("Listing Form 16 Part A records for financial year {}", financialYear);
        List<PartAOfficerItem> items = form16PartAService.list(financialYear);
        return PartAResponse.ok("Form 16 Part A records retrieved successfully", items);
    }

    /**
     * Employee self-service endpoint to retrieve their own Form 16 Part A signed download link.
     */
    @GetMapping("/api/v1/me/form16/{fy}/part-a")
    @RequiresAction("payroll.payslip.read_own")
    public PartAResponse<PartAEmployeeResponse> own(@PathVariable("fy") String financialYear) {
        log.info("Retrieving own Form 16 Part A for financial year {}", financialYear);
        PartAEmployeeResponse response = form16PartAService.own(financialYear);
        return PartAResponse.ok("Form 16 Part A retrieved successfully", response);
    }
}
