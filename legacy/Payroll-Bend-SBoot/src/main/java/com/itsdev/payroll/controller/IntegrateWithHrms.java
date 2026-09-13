package com.itsdev.payroll.controller;

import com.itsdev.payroll.service.IntegrateWithHrmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class IntegrateWithHrms {

    @Autowired
    private IntegrateWithHrmsService integrateWithHrmsService;

    @GetMapping("/test-payroll")
    public String requestHRMS() {
        return integrateWithHrmsService.getResponseFromHRMS();
    }
}
