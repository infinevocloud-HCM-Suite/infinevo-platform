package com.itsdev.payroll.controller;

import com.itsdev.payroll.dto.PayScheduleDTO;
import com.itsdev.payroll.service.PayScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paySchedule")
public class PayScheduleController {

    private final PayScheduleService payScheduleService;

    public PayScheduleController(PayScheduleService payScheduleService) {
        this.payScheduleService = payScheduleService;
    }

    @PostMapping
    public ResponseEntity<PayScheduleDTO> create(@RequestHeader("organizationId") String organizationId,
                                                 @RequestBody PayScheduleDTO dto) {
        return ResponseEntity.ok(payScheduleService.create(organizationId, dto));
    }

    @GetMapping
    public ResponseEntity<PayScheduleDTO> get(@RequestHeader("organizationId") String organizationId) {
        return ResponseEntity.ok(payScheduleService.getByOrganizationId(organizationId));
    }

    @PutMapping("/{payScheduleId}")
    public ResponseEntity<PayScheduleDTO> update(@RequestHeader("organizationId") String organizationId,
                                                 @PathVariable String payScheduleId,
                                                 @RequestBody PayScheduleDTO dto) {
        return ResponseEntity.ok(payScheduleService.update(organizationId, payScheduleId, dto));
    }
}
