package com.itsdev.payroll.serviceimpl.payruns;

import com.itsdev.payroll.service.payruns.PayslipTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class PayslipTokenServiceImpl implements PayslipTokenService {

    @Value("${app.payslip.download.secret-key:default-payslip-secret-key-2026-xyz}")
    private String secretKey;

    @Override
    public String generateToken(String payrunId, String employeeId, String orgId) {
        try {
            String message = payrunId + ":" + employeeId + ":" + orgId;
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKeySpec);
            byte[] hash = sha256HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC token for payslip", e);
        }
    }

    @Override
    public boolean verifyToken(String payrunId, String employeeId, String orgId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String expected = generateToken(payrunId, employeeId, orgId);
        return expected.equals(token);
    }
}
