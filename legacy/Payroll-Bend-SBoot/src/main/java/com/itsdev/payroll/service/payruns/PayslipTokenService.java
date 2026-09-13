package com.itsdev.payroll.service.payruns;

public interface PayslipTokenService {
    String generateToken(String payrunId, String employeeId, String orgId);
    boolean verifyToken(String payrunId, String employeeId, String orgId, String token);
}
