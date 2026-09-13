package com.itsdev.payroll.serviceimpl;

import com.itsdev.payroll.dto.IntegrateWithHrms.LeaveRequestDTO;
import com.itsdev.payroll.dto.IntegrateWithHrms.LeaveResponseDTO;
import com.itsdev.payroll.service.IntegrateWithHrmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntegrateWithHrmsServiceImpl implements IntegrateWithHrmsService {

    private static final Logger log = LoggerFactory.getLogger(IntegrateWithHrmsServiceImpl.class);

    @Autowired
    private WebClient webClient;

    public String getResponseFromHRMS() {
        return webClient.get()
                .uri("/public/test-hrms")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public Map<String, Double> fetchLeaves(List<String> employeeEmails, String payPeriod) {
        String method = "fetchLeaves";
        try {
            LeaveRequestDTO request = new LeaveRequestDTO();
            request.setEmployeeEmails(employeeEmails);
            request.setPayPeriod(payPeriod);

            log.info("[{}] 📤 Sending leave request for {} employees | payPeriod={}",
                    method, employeeEmails.size(), payPeriod);

            LeaveResponseDTO[] responses = webClient.post()
                    .uri("/public/get-employee-leaves")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LeaveResponseDTO[].class)
                    .block();

            Map<String, Double> leaveMap = new HashMap<>();
            if (responses != null) {
                for (LeaveResponseDTO r : responses) {
                    leaveMap.put(r.getEmployeeEmail(), r.getTotalLeaves() != null ? r.getTotalLeaves() : 0.0);
                }
            }

            log.info("[{}] ✅ Received leave data for {} employees", method, leaveMap.size());
            return leaveMap;

        } catch (Exception ex) {
            log.error("[{}] ❌ Error calling HRMS API: {}", method, ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
