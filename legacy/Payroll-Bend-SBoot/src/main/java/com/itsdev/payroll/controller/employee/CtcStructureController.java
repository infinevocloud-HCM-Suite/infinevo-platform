package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.CtcStructureDTO;
import com.itsdev.payroll.dto.employee.EmployeeCTCDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionListDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.ProcessLaterRevisionDTO;
import com.itsdev.payroll.service.employee.CtcStructureService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@RestController
//@RequestMapping("/api/v1/ctc-structures")
//public class CtcStructureController {
//
//    private final CtcStructureService ctcStructureService;
//
//    public CtcStructureController(CtcStructureService ctcStructureService) {
//        this.ctcStructureService = ctcStructureService;
//    }
//
//    // CREATE
//// in CtcStructureController
//    @PostMapping
//    public ResponseEntity<EmployeeCTCDTO> create(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestBody EmployeeCTCDTO dto
//    ) {
//        return ResponseEntity.ok(ctcStructureService.create(organizationId, dto));
//    }
//
//
//    // UPDATE
//    @PutMapping("/{id}")
//    public ResponseEntity<EmployeeCTCDTO> update(@RequestHeader("organizationId") String organizationId,
//                                                 @PathVariable Long id,
//                                                 @RequestBody EmployeeCTCDTO dto) {
//        return ResponseEntity.ok(ctcStructureService.update(organizationId, id, dto));
//    }
//
//    // GET by ID
//    @GetMapping("/{id}")
//    public ResponseEntity<EmployeeCTCDTO> get(@RequestHeader("organizationId") String organizationId,
//                                              @PathVariable Long id) {
//        return ResponseEntity.ok(ctcStructureService.get(organizationId, id));
//    }
//
//    // LIST all for org
//    @GetMapping
//    public ResponseEntity<List<EmployeeCTCDTO>> list(@RequestHeader("organizationId") String organizationId) {
//        return ResponseEntity.ok(ctcStructureService.list(organizationId));
//    }
//
//    // DELETE
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> delete(@RequestHeader("organizationId") String organizationId,
//                                       @PathVariable Long id) {
//        ctcStructureService.delete(organizationId, id);
//        return ResponseEntity.noContent().build();
//    }
//
//
//    // GET CTC by employeeId
//    @GetMapping("/by-employee/{employeeId}")
//    public ResponseEntity<List<EmployeeCTCDTO>> getAllByEmployeeId(
//            @RequestHeader("organizationId") String organizationId,
//            @PathVariable Long employeeId) {
//        return ResponseEntity.ok(ctcStructureService.getAllByEmployeeId(organizationId, employeeId));
//    }
//
//}


@RestController
@RequestMapping("/api/v1/ctc-structures")
public class CtcStructureController {

    private final CtcStructureService ctcStructureService;
    
	 private static final Logger log = LoggerFactory.getLogger(CtcStructureController.class);

    public CtcStructureController(CtcStructureService ctcStructureService) {
        this.ctcStructureService = ctcStructureService;
    }

    // CREATE
//    @PostMapping
//    public ResponseEntity<Map<String, Object>> create(
//            @RequestHeader("organizationId") String organizationId,
//            @RequestBody EmployeeCTCDTO dto) {
//
//        EmployeeCTCDTO created = ctcStructureService.create(organizationId, dto);
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("status", 201);
//        response.put("message", "CTC structure created successfully");
//        response.put("data", created);
//
//        return ResponseEntity.ok(response);
//    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeeCTCDTO dto) {

        EmployeeCTCDTO created = ctcStructureService.create(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "CTC structure created successfully");
        response.put("data", created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    // UPDATE
    @PutMapping
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader("organizationId") String organizationId,         
            @RequestBody EmployeeCTCDTO dto) {

        EmployeeCTCDTO updated = ctcStructureService.update(organizationId,  dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC structure updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    // GET by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long id) {

        EmployeeCTCDTO dto = ctcStructureService.get(organizationId, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC structure retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    // LIST all for org
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader("organizationId") String organizationId) {

        List<EmployeeCTCDTO> list = ctcStructureService.list(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC structures retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long id) {

        ctcStructureService.delete(organizationId, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC structure deleted successfully");

        return ResponseEntity.ok(response);
    }

    // GET CTC by employeeId
    @GetMapping("/by-employee/{employeeId}")
    public ResponseEntity<Map<String, Object>> getAllByEmployeeId(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long employeeId) {

        List<EmployeeCTCDTO> list = ctcStructureService.getAllByEmployeeId(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC structures for employee retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/by-email")
    public ResponseEntity<Map<String, Object>> getByWorkMail(@RequestParam String email) {
        String method = "getByWorkMail";
        log.info("[{}] 📥 Incoming request with email: {}", method, email);

        CtcStructureDTO dto = ctcStructureService.getSalaryStructureByWorkMail(email);

        log.info("[{}] ✅ Successfully fetched salary structure for email: {}", method, email);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Salary structure fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }



    //revise ctc

    @PostMapping("/revise")
    public ResponseEntity<Map<String, Object>> revise(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeeCtcRevisionDTO dto) {
        EmployeeCTCDTO revised = ctcStructureService.revise(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 201);
        response.put("message", "CTC revision saved successfully");
        response.put("data", revised);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/revision/update")
    public ResponseEntity<Map<String, Object>> updateRevision(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeeCtcRevisionDTO dto
    ) {
        EmployeeCTCDTO updated = ctcStructureService.updateRevision(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC revision updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/revision/delete")
    public ResponseEntity<Map<String, Object>> deleteRevision(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(required = false) Long revisionId,
            @RequestParam(required = false) Long ctcStructureId
    ) {

        if (revisionId == null && ctcStructureId == null) {
            throw new IllegalArgumentException(
                    "Either revisionId or ctcStructureId must be provided"
            );
        }

        ctcStructureService.deleteRevision(organizationId, revisionId, ctcStructureId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "CTC revision deleted successfully");

        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }





    @GetMapping("/revision")
    public ResponseEntity<Map<String, Object>> getRevision(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(required = false) Long revisionId,
            @RequestParam(required = false) Long ctcStructureId
    ) {

        EmployeeCTCDTO data = ctcStructureService.getRevision(organizationId, revisionId, ctcStructureId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "CTC revision fetched successfully");
        resp.put("data", data);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/revision/list")
    public ResponseEntity<Map<String, Object>> getAllRevisedCtcs(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<EmployeeCtcRevisionListDTO> result =
                ctcStructureService.getAllRevisedCtcs(organizationId, page, size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "CTC revisions fetched successfully");
        response.put("data", result.getContent());

        Map<String, Object> pageContext = new HashMap<>();
        pageContext.put("page", page);
        pageContext.put("per_page", size);
        pageContext.put("has_more_page", result.hasNext());

        response.put("page_context", pageContext);

        return ResponseEntity.ok(response);
    }



    @GetMapping("/revisions/export")
    public ResponseEntity<byte[]> exportRevisedCtcs(
            @RequestHeader("organizationId") String organizationId
    ) {

        byte[] excel = ctcStructureService.exportRevisedCtcs(organizationId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=salary_revisions.xlsx")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )

                .body(excel);
    }

    @PostMapping("/revisions/process-later")
    public ResponseEntity<Map<String, Object>> processLater(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody ProcessLaterRevisionDTO dto
    ) {

        ctcStructureService.processLaterRevisions(organizationId, dto);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "CTC revisions marked for process later");

        return ResponseEntity.ok(resp);
    }


}

