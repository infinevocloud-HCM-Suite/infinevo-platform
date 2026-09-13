package com.itsdev.payroll.service;

import java.util.List;
import java.util.Map;

public interface IntegrateWithHrmsService {

    String getResponseFromHRMS();

    Map<String, Double> fetchLeaves(List<String> employeeEmails, String payPeriod);
}
