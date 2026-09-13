package com.itsdev.payroll.serviceimpl.leave;



import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.itsdev.payroll.dto.leave.*;

import com.itsdev.payroll.entity.employee.BasicDetails;

import com.itsdev.payroll.entity.leave.EmployeeLeaveAllocation;

import com.itsdev.payroll.entity.leave.EmployeeLeaveBalanceConsumption;

import com.itsdev.payroll.repository.employee.BasicDetailsRepository;

import com.itsdev.payroll.repository.leave.EmployeeLeaveAllocationRepository;

import com.itsdev.payroll.repository.leave.EmployeeLeaveBalanceConsumptionRepository;

import com.itsdev.payroll.service.leave.EmployeeLeaveAllocationService;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.JdbcTemplate;

import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.time.LocalDate;

import java.time.LocalDateTime;

import java.util.*;

import java.util.stream.Collectors;



@Service

public class EmployeeLeaveAllocationServiceImpl implements EmployeeLeaveAllocationService {



    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();



    private final EmployeeLeaveAllocationRepository allocationRepository;

    private final BasicDetailsRepository basicDetailsRepository;

    private final EmployeeLeaveBalanceConsumptionRepository consumptionRepository;

    private final JdbcTemplate jdbcTemplate;



    public EmployeeLeaveAllocationServiceImpl(

            EmployeeLeaveAllocationRepository allocationRepository,

            BasicDetailsRepository basicDetailsRepository,

            EmployeeLeaveBalanceConsumptionRepository consumptionRepository,

            JdbcTemplate jdbcTemplate) {

        this.allocationRepository = allocationRepository;

        this.basicDetailsRepository = basicDetailsRepository;

        this.consumptionRepository = consumptionRepository;

        this.jdbcTemplate = jdbcTemplate;

    }



    @PostConstruct

    public void initSchemaIndexes() {

        try {

            List<String> indexNames = jdbcTemplate.queryForList(

                "SELECT DISTINCT index_name FROM information_schema.statistics " +

                "WHERE table_schema = DATABASE() AND table_name = 'employee_leave_allocation' AND index_name != 'PRIMARY' AND non_unique = 0",

                String.class

            );

            for (String idx : indexNames) {

                List<String> columns = jdbcTemplate.queryForList(

                    "SELECT column_name FROM information_schema.statistics " +

                    "WHERE table_schema = DATABASE() AND table_name = 'employee_leave_allocation' AND index_name = ? ORDER BY seq_in_index",

                    String.class, idx

                );

                if (!columns.contains("year")) {

                    try {

                        jdbcTemplate.execute("ALTER TABLE employee_leave_allocation DROP INDEX `" + idx + "`");

                        System.out.println("Dropped obsolete unique index without year: " + idx);

                    } catch (Exception ex) {

                        System.err.println("Could not drop index " + idx + ": " + ex.getMessage());

                    }

                }

            }

            try {

                jdbcTemplate.execute(

                    "ALTER TABLE employee_leave_allocation ADD UNIQUE KEY uk_emp_leave_alloc (organization_id, employee_id, leave_type, year)"

                );

                System.out.println("Ensured unique key uk_emp_leave_alloc with year column.");

            } catch (Exception ex) {

                // Index already exists

            }

        } catch (Exception e) {

            System.err.println("Note on initSchemaIndexes: " + e.getMessage());

        }

    }



    private Map<String, Integer> parseMonthlyBreakdown(String json) {

        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {

            return new LinkedHashMap<>();

        }

        try {

            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Integer>>() {});

        } catch (Exception e) {

            return new LinkedHashMap<>();

        }

    }



    private String serializeMonthlyBreakdown(Map<String, Integer> map) {

        if (map == null || map.isEmpty()) {

            return "{}";

        }

        try {

            return OBJECT_MAPPER.writeValueAsString(map);

        } catch (Exception e) {

            return "{}";

        }

    }



    @Override

    @Transactional

    public List<EmployeeLeaveAllocationResponseDTO> saveBulkAllocations(

            String organizationId,

            BulkLeaveAllocationRequestDTO request,

            String createdBy) {



        if (request.getAllocations() == null || request.getAllocations().isEmpty()) {

            throw new RuntimeException("No employee allocations provided");

        }



        String year = request.getYear();

        int parsedYear = (year != null && year.matches("\\d{4}")) ? Integer.parseInt(year) : LocalDate.now().getYear();

        LocalDate yearStartDate = LocalDate.of(parsedYear, 1, 1);

        LocalDate yearEndDate = LocalDate.of(parsedYear, 12, 31);

        LocalDate today = LocalDate.now();



        for (EmployeeLeaveAllocationRequestDTO empAlloc : request.getAllocations()) {

            String employeeId = empAlloc.getEmployeeId();



            BasicDetails emp = basicDetailsRepository.findByEmployeeId(employeeId)

                    .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + employeeId));



            if (emp.getOrganization() == null ||

                    !organizationId.equals(emp.getOrganization().getOrganizationId())) {

                throw new RuntimeException("Employee " + employeeId + " does not belong to this organization");

            }



            Set<String> seenTypes = new HashSet<>();

            for (LeaveAllocationItemDTO item : empAlloc.getLeaveTypes()) {

                String typeName = item.getLeaveType().trim();

                if (!seenTypes.add(typeName.toLowerCase())) {

                    throw new RuntimeException("Duplicate leave type '" + typeName + "' for employee " + emp.getFirstName());

                }



                if (item.getExpirationDate() == null) {

                    throw new RuntimeException("Expiration date is required for '" + typeName + "' (" + emp.getFirstName() + ")");

                }



                if (item.getExpirationDate().isBefore(yearStartDate) || item.getExpirationDate().isAfter(yearEndDate)) {

                    throw new RuntimeException("Expiration date for '" + typeName + "' (" + emp.getFirstName() + ") must be within year " + year);

                }



                if (parsedYear == today.getYear() && item.getExpirationDate().isBefore(today)) {

                    throw new RuntimeException("Expiration date for '" + typeName + "' (" + emp.getFirstName() + ") cannot be a past date");

                }



                Optional<EmployeeLeaveAllocation> existingOpt = allocationRepository

                        .findByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(organizationId, employeeId, typeName, year);



                int annual = item.getAnnualDays() != null ? item.getAnnualDays() : (item.getRemainingDays() != null ? item.getRemainingDays() : 0);



                EmployeeLeaveAllocation entity;

                if (existingOpt.isPresent()) {

                    entity = existingOpt.get();

                    entity.setAnnualDays(annual);

                    if (item.getCarriedForwardDays() != null) {

                        entity.setCarriedForwardDays(item.getCarriedForwardDays());

                    }

                    entity.setExpirationDate(item.getExpirationDate());

                    entity.setCarryForward(Boolean.TRUE.equals(item.getCarryForward()));

                    entity.setUpdatedAt(LocalDateTime.now());

                } else {

                    entity = new EmployeeLeaveAllocation();

                    entity.setOrganizationId(organizationId);

                    entity.setEmployeeId(employeeId);

                    entity.setLeaveType(typeName);

                    entity.setYear(year);

                    entity.setAnnualDays(annual);

                    entity.setCarriedForwardDays(item.getCarriedForwardDays() != null ? item.getCarriedForwardDays() : 0);

                    entity.setConsumedDays(0);

                    entity.setExpirationDate(item.getExpirationDate());

                    entity.setCarryForward(Boolean.TRUE.equals(item.getCarryForward()));

                    entity.setCreatedBy(createdBy);

                    entity.setCreatedAt(LocalDateTime.now());

                    entity.setUpdatedAt(LocalDateTime.now());

                }



                allocationRepository.save(entity);

            }

        }



        return getAllocations(organizationId, year);

    }



    @Override

    @Transactional

    public EmployeeLeaveAllocationResponseDTO updateEmployeeAllocation(

            String organizationId,

            String employeeId,

            String year,

            List<LeaveAllocationItemDTO> leaveTypes,

            String updatedBy) {



        BasicDetails emp = basicDetailsRepository.findByEmployeeId(employeeId)

                    .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + employeeId));



        if (emp.getOrganization() == null ||

                !organizationId.equals(emp.getOrganization().getOrganizationId())) {

            throw new RuntimeException("Employee " + employeeId + " does not belong to this organization");

        }



        if (leaveTypes == null || leaveTypes.isEmpty()) {

            throw new RuntimeException("At least one leave type is required");

        }



        List<EmployeeLeaveAllocation> existingAllocations = allocationRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);



        Map<String, EmployeeLeaveAllocation> existingMap = existingAllocations.stream()

                .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (a1, a2) -> a1));



        Set<String> updatedTypeNames = new HashSet<>();



        int parsedYear = (year != null && year.matches("\\d{4}")) ? Integer.parseInt(year) : LocalDate.now().getYear();



        for (LeaveAllocationItemDTO item : leaveTypes) {

            String typeName = item.getLeaveType().trim();

            if (!updatedTypeNames.add(typeName.toLowerCase())) {

                throw new RuntimeException("Duplicate leave type '" + typeName + "' for employee " + emp.getFirstName());

            }



            EmployeeLeaveAllocation entity = existingMap.get(typeName.toLowerCase());

            LocalDate expDate = item.getExpirationDate() != null 

                    ? item.getExpirationDate() 

                    : (entity != null && entity.getExpirationDate() != null 

                            ? entity.getExpirationDate() 

                            : LocalDate.of(parsedYear, 12, 31));



            LocalDate yearStartDate = LocalDate.of(parsedYear, 1, 1);

            LocalDate yearEndDate = LocalDate.of(parsedYear, 12, 31);

            LocalDate today = LocalDate.now();



            if (expDate.isBefore(yearStartDate) || expDate.isAfter(yearEndDate)) {

                throw new RuntimeException("Expiration date for '" + typeName + "' (" + emp.getFirstName() + ") must be within year " + year);

            }



            if (parsedYear == today.getYear() && expDate.isBefore(today)) {

                throw new RuntimeException("Expiration date for '" + typeName + "' (" + emp.getFirstName() + ") cannot be a past date");

            }



            int carried = item.getCarriedForwardDays() != null 

                    ? item.getCarriedForwardDays() 

                    : (entity != null && entity.getCarriedForwardDays() != null ? entity.getCarriedForwardDays() : 0);



            List<EmployeeLeaveBalanceConsumption> consList = consumptionRepository

                    .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year)

                    .stream()

                    .filter(c -> c.getLeaveType() != null && c.getLeaveType().equalsIgnoreCase(typeName))

                    .toList();

            int consumed = consList.stream()

                    .mapToInt(c -> c.getConsumedDays() != null ? c.getConsumedDays() : 0)

                    .sum();




            int initialAnnual = entity != null && entity.getAnnualDays() != null ? entity.getAnnualDays() : 12;

            int initialAvail = initialAnnual + carried;

            int initialRemaining = Math.max(0, initialAvail - consumed);



            int annual;

            if (item.getRemainingDays() != null) {

                int delta = item.getRemainingDays() - initialRemaining;

                annual = Math.max(1, initialAnnual + delta);

            } else if (item.getAnnualDays() != null) {

                annual = item.getAnnualDays();

            } else {

                annual = initialAnnual;

            }



            int totalAvail = annual + carried;

            int newLop = Math.max(0, consumed - totalAvail);



            if (entity != null) {

                entity.setAnnualDays(annual);

                entity.setCarriedForwardDays(carried);

                entity.setConsumedDays(consumed);
                entity.setLopDays(newLop);

                entity.setExpirationDate(expDate);

                entity.setCarryForward(Boolean.TRUE.equals(item.getCarryForward()));

                entity.setUpdatedAt(LocalDateTime.now());

                allocationRepository.save(entity);

            } else {

                EmployeeLeaveAllocation newEntity = new EmployeeLeaveAllocation();

                newEntity.setOrganizationId(organizationId);

                newEntity.setEmployeeId(employeeId);

                newEntity.setLeaveType(typeName);

                newEntity.setYear(year);

                newEntity.setAnnualDays(annual);

                newEntity.setCarriedForwardDays(carried);

                newEntity.setConsumedDays(consumed);

                newEntity.setLopDays(newLop);

                newEntity.setExpirationDate(expDate);

                newEntity.setCarryForward(Boolean.TRUE.equals(item.getCarryForward()));

                newEntity.setCreatedBy(updatedBy);

                newEntity.setCreatedAt(LocalDateTime.now());

                newEntity.setUpdatedAt(LocalDateTime.now());

                allocationRepository.save(newEntity);

            }



            // Sync balance_days, balance_after, running_ytd and lop_days in employee_leave_balance_consumption

            recalculateConsumptionBalances(organizationId, employeeId, typeName, year, totalAvail);

        }



        for (EmployeeLeaveAllocation old : existingAllocations) {

            if (!updatedTypeNames.contains(old.getLeaveType().toLowerCase())) {

                List<EmployeeLeaveBalanceConsumption> oldConsList = consumptionRepository

                        .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year)

                        .stream()

                        .filter(c -> c.getLeaveType() != null && c.getLeaveType().equalsIgnoreCase(old.getLeaveType()))

                        .toList();

                int consumed = oldConsList.stream()

                        .mapToInt(c -> c.getConsumedDays() != null ? c.getConsumedDays() : 0)

                        .sum();

                if (consumed == 0 && old.getConsumedDays() != null) {

                    consumed = old.getConsumedDays();

                }



                if (consumed > 0) {

                    throw new RuntimeException("Cannot remove leave type '" + old.getLeaveType() +

                            "' because " + consumed + " day(s) have already been consumed.");

                }

                allocationRepository.delete(old);

            }

        }



        return getEmployeeAllocation(organizationId, employeeId, year);

    }



    private void recalculateConsumptionBalances(

            String organizationId,

            String employeeId,

            String leaveType,

            String year,

            int totalAlloc) {



        List<EmployeeLeaveBalanceConsumption> rows =

                consumptionRepository.findByOrganizationIdAndEmployeeIdAndYearOrderByIdAsc(organizationId, employeeId, year);



        int runningYtd = 0;

        for (EmployeeLeaveBalanceConsumption row : rows) {

            if (row.getLeaveType() != null && row.getLeaveType().equalsIgnoreCase(leaveType)) {

                int taken = row.getConsumedDays() != null ? row.getConsumedDays() : 0;

                runningYtd += taken;

                int balanceAfter = Math.max(0, totalAlloc - runningYtd);

                int lop = runningYtd > totalAlloc ? (runningYtd - totalAlloc) : 0;



                row.setRunningYtd(runningYtd);

                row.setBalanceAfter(balanceAfter);

                row.setBalanceDays(balanceAfter);

                row.setLopDays(lop);

                row.setUpdatedAt(LocalDateTime.now());

                consumptionRepository.save(row);

            }

        }

    }



    private LeaveAllocationItemDTO buildLeaveAllocationItem(

            EmployeeLeaveAllocation a,

            List<EmployeeLeaveBalanceConsumption> consumptionList) {



        int annual = (a.getAnnualDays() != null ? a.getAnnualDays() : 0);

        int carried = (a.getCarriedForwardDays() != null ? a.getCarriedForwardDays() : 0);

        int total = annual + carried;



        int consumed = 0;

        int lop = 0;

        int lwp = 0;

        String latestLeaveMonth = a.getLeaveMonth();

        Map<String, Integer> mergedMonthly = new LinkedHashMap<>();

        Map<String, Integer> mergedMonthlyLop = new LinkedHashMap<>();

        Map<String, Integer> mergedMonthlyLwp = new LinkedHashMap<>();



        if (consumptionList != null && !consumptionList.isEmpty()) {

            for (EmployeeLeaveBalanceConsumption c : consumptionList) {

                if (c.getConsumedDays() != null) {

                    consumed += c.getConsumedDays();

                }

                if (c.getLopDays() != null && c.getLopDays() > 0) {

                    lop += c.getLopDays();

                }

                if (c.getLwp() != null && c.getLwp() > 0) {

                    lwp += c.getLwp();

                }

                if (c.getLeaveMonth() != null && !c.getLeaveMonth().isBlank()) {

                    latestLeaveMonth = c.getLeaveMonth();

                }

                for (Map.Entry<String, Integer> e : parseMonthlyBreakdown(c.getMonthlyBreakdown()).entrySet()) {

                    mergedMonthly.merge(e.getKey(), e.getValue() != null ? e.getValue() : 0, Integer::sum);

                }

                for (Map.Entry<String, Integer> e : parseMonthlyBreakdown(c.getMonthlyLopBreakdown()).entrySet()) {

                    mergedMonthlyLop.merge(e.getKey(), e.getValue() != null ? e.getValue() : 0, Integer::sum);

                }

                for (Map.Entry<String, Integer> e : parseMonthlyBreakdown(c.getMonthlyLwpBreakdown()).entrySet()) {

                    mergedMonthlyLwp.merge(e.getKey(), e.getValue() != null ? e.getValue() : 0, Integer::sum);

                }

            }

        } else {
            // When consumptionList has no records, consumed and LOP are strictly 0
            consumed = 0;
            lop = 0;
            lwp = 0;
        }



        int balance = Math.max(0, total - consumed);

        if (consumed <= total) {
            lop = 0;
            lwp = 0;
        } else if (lop == 0) {

            lop = consumed - total;

        }

        if (lwp == 0 && lop > 0) {

            lwp = lop;

        }



        LeaveAllocationItemDTO item = new LeaveAllocationItemDTO();

        item.setId(a.getId());

        item.setLeaveType(a.getLeaveType());

        item.setAnnualDays(a.getAnnualDays());

        item.setCarriedForwardDays(a.getCarriedForwardDays());

        item.setTotalDays(total);

        item.setConsumedDays(consumed);

        item.setBalanceDays(balance);

        item.setRemainingDays(balance);

        item.setLopDays(lop);

        item.setLwp(lwp);

        item.setLeaveMonth(latestLeaveMonth);

        item.setMonthlyBreakdown(mergedMonthly);

        item.setMonthlyLopBreakdown(mergedMonthlyLop);

        item.setMonthlyLwpBreakdown(mergedMonthlyLwp);

        item.setExpirationDate(a.getExpirationDate());

        item.setCarryForward(a.getCarryForward());



        return item;

    }



    @Override

    @Transactional(readOnly = true)

    public List<EmployeeLeaveAllocationResponseDTO> getAllocations(String organizationId, String year) {

        List<EmployeeLeaveAllocation> allAllocations = allocationRepository

                .findByOrganizationIdAndYear(organizationId, year);



        List<EmployeeLeaveBalanceConsumption> allConsumptions = (year != null && !year.isBlank())

                ? consumptionRepository.findByOrganizationIdAndYear(organizationId, year)

                : consumptionRepository.findByOrganizationId(organizationId);



        Map<String, List<EmployeeLeaveBalanceConsumption>> consumptionMap = new HashMap<>();

        for (EmployeeLeaveBalanceConsumption c : allConsumptions) {

            String key = c.getEmployeeId() + "_" + (c.getLeaveType() != null ? c.getLeaveType().toLowerCase().trim() : "");

            consumptionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(c);

        }



        Map<String, BasicDetails> empMap = new HashMap<>();

        try {

            List<BasicDetails> allEmpList = basicDetailsRepository.findByOrganization_OrganizationId(organizationId);

            for (BasicDetails b : allEmpList) {

                if (b.getEmployeeId() != null) {

                    empMap.put(b.getEmployeeId(), b);

                }

            }

        } catch (Exception ex) {

            // fallback gracefully

        }



        Map<String, List<EmployeeLeaveAllocation>> groupedByEmp = allAllocations.stream()

                .collect(Collectors.groupingBy(EmployeeLeaveAllocation::getEmployeeId));



        List<EmployeeLeaveAllocationResponseDTO> result = new ArrayList<>();



        for (Map.Entry<String, List<EmployeeLeaveAllocation>> entry : groupedByEmp.entrySet()) {

            String employeeId = entry.getKey();

            List<EmployeeLeaveAllocation> allocations = entry.getValue();



            EmployeeLeaveAllocationResponseDTO dto = new EmployeeLeaveAllocationResponseDTO();

            dto.setEmployeeId(employeeId);

            dto.setYear(year);



            if (!allocations.isEmpty()) {

                EmployeeLeaveAllocation first = allocations.get(0);

                dto.setCreatedBy(first.getCreatedBy());

                dto.setCreatedAt(first.getCreatedAt());

                dto.setUpdatedAt(allocations.stream()

                        .map(EmployeeLeaveAllocation::getUpdatedAt)

                        .filter(Objects::nonNull)

                        .max(LocalDateTime::compareTo)

                        .orElse(first.getUpdatedAt()));

            }



            BasicDetails emp = empMap.get(employeeId);

            if (emp == null) {

                emp = basicDetailsRepository.findByEmployeeId(employeeId).orElse(null);

            }

            if (emp != null) {

                dto.setEmployeeNumber(emp.getEmployeeNumber());

                String fullName = ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " +

                        (emp.getLastName() != null ? emp.getLastName() : "")).trim();

                dto.setEmployeeName(fullName);

            }



            int totalAllocated = 0;

            int totalConsumed = 0;

            int totalRemaining = 0;

            int totalLop = 0;

            int totalLwp = 0;



            List<LeaveAllocationItemDTO> itemDTOs = new ArrayList<>();

            for (EmployeeLeaveAllocation a : allocations) {

                String consKey = a.getEmployeeId() + "_" + (a.getLeaveType() != null ? a.getLeaveType().toLowerCase().trim() : "");

                List<EmployeeLeaveBalanceConsumption> cList = consumptionMap.getOrDefault(consKey, Collections.emptyList());

                LeaveAllocationItemDTO item = buildLeaveAllocationItem(a, cList);



                totalAllocated += item.getTotalDays();

                totalConsumed += item.getConsumedDays();

                totalRemaining += item.getBalanceDays();

                totalLop += item.getLopDays();

                totalLwp += item.getLwp();



                itemDTOs.add(item);

            }



            dto.setTotalAllocatedDays(totalAllocated);

            dto.setTotalConsumedDays(totalConsumed);

            dto.setTotalRemainingDays(totalRemaining);

            dto.setTotalLopDays(totalLop);

            dto.setTotalLwp(totalLwp);

            if (!allocations.isEmpty()) { dto.setLeaveMonth(allocations.get(0).getLeaveMonth()); }

            dto.setLeaveTypes(itemDTOs);

            result.add(dto);

        }



        // Descending sort: most recently updated / created employee first

        result.sort((a, b) -> {

            LocalDateTime tA = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();

            LocalDateTime tB = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();

            if (tA != null && tB != null) {

                int cmp = tB.compareTo(tA);

                if (cmp != 0) return cmp;

            } else if (tA != null) {

                return -1;

            } else if (tB != null) {

                return 1;

            }

            String nameA = a.getEmployeeName() != null ? a.getEmployeeName() : "";

            String nameB = b.getEmployeeName() != null ? b.getEmployeeName() : "";

            return nameA.compareToIgnoreCase(nameB);

        });



        return result;

    }



    @Override

    @Transactional(readOnly = true)

    public Map<String, Object> getAllocationsPaginated(String organizationId, String year, String search, int page, int size) {

        List<EmployeeLeaveAllocationResponseDTO> all = getAllocations(organizationId, year);



        List<EmployeeLeaveAllocationResponseDTO> filtered = all;

        if (search != null && !search.trim().isEmpty()) {

            String term = search.trim().toLowerCase();

            filtered = all.stream().filter(dto -> {

                String name = dto.getEmployeeName() != null ? dto.getEmployeeName().toLowerCase() : "";

                String num = dto.getEmployeeNumber() != null ? dto.getEmployeeNumber().toLowerCase() : "";

                String empId = dto.getEmployeeId() != null ? dto.getEmployeeId().toLowerCase() : "";

                return name.contains(term) || num.contains(term) || empId.contains(term);

            }).collect(Collectors.toList());

        }



        int totalElements = filtered.size();

        int safePage = Math.max(0, page);

        int safeSize = size > 0 ? size : 10;

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);



        int fromIndex = Math.min(safePage * safeSize, totalElements);

        int toIndex = Math.min(fromIndex + safeSize, totalElements);



        List<EmployeeLeaveAllocationResponseDTO> content = (fromIndex < toIndex)

                ? filtered.subList(fromIndex, toIndex)

                : Collections.emptyList();



        Map<String, Object> response = new HashMap<>();

        response.put("content", content);

        response.put("totalElements", totalElements);

        response.put("totalPages", totalPages);

        response.put("pageNumber", safePage);

        response.put("pageSize", safeSize);

        return response;

    }



    @Override

    @Transactional(readOnly = true)

    public EmployeeLeaveAllocationResponseDTO getEmployeeAllocation(String organizationId, String employeeId, String year) {

        List<EmployeeLeaveAllocation> allocations = allocationRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);



        List<EmployeeLeaveBalanceConsumption> consumptions = consumptionRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);



        Map<String, List<EmployeeLeaveBalanceConsumption>> consumptionMap = new HashMap<>();

        for (EmployeeLeaveBalanceConsumption c : consumptions) {

            if (c.getLeaveType() != null) {

                String key = c.getLeaveType().toLowerCase().trim();

                consumptionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(c);

            }

        }



        EmployeeLeaveAllocationResponseDTO dto = new EmployeeLeaveAllocationResponseDTO();

        dto.setEmployeeId(employeeId);

        dto.setYear(year);



        BasicDetails emp = basicDetailsRepository.findByEmployeeId(employeeId).orElse(null);

        if (emp != null) {

            dto.setEmployeeNumber(emp.getEmployeeNumber());

            String fullName = ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " +

                    (emp.getLastName() != null ? emp.getLastName() : "")).trim();

            dto.setEmployeeName(fullName);

        }



        int totalAllocated = 0;

        int totalConsumed = 0;

        int totalRemaining = 0;

        int totalLop = 0;

        int totalLwp = 0;



        List<LeaveAllocationItemDTO> itemDTOs = new ArrayList<>();

        for (EmployeeLeaveAllocation a : allocations) {

            String key = a.getLeaveType() != null ? a.getLeaveType().toLowerCase().trim() : "";

            List<EmployeeLeaveBalanceConsumption> cList = consumptionMap.getOrDefault(key, Collections.emptyList());

            LeaveAllocationItemDTO item = buildLeaveAllocationItem(a, cList);



            totalAllocated += item.getTotalDays();

            totalConsumed += item.getConsumedDays();

            totalRemaining += item.getBalanceDays();

            totalLop += item.getLopDays();

            totalLwp += item.getLwp();



            itemDTOs.add(item);

        }



        dto.setTotalAllocatedDays(totalAllocated);

        dto.setTotalConsumedDays(totalConsumed);

        dto.setTotalRemainingDays(totalRemaining);

        dto.setTotalLopDays(totalLop);

        dto.setTotalLwp(totalLwp);

        if (!allocations.isEmpty()) {

            EmployeeLeaveAllocation first = allocations.get(0);

            dto.setLeaveMonth(first.getLeaveMonth());

            dto.setCreatedBy(first.getCreatedBy());

            dto.setCreatedAt(first.getCreatedAt());

            dto.setUpdatedAt(allocations.stream()

                    .map(EmployeeLeaveAllocation::getUpdatedAt)

                    .filter(Objects::nonNull)

                    .max(LocalDateTime::compareTo)

                    .orElse(first.getUpdatedAt()));

        }

        dto.setLeaveTypes(itemDTOs);

        return dto;

    }



    @Override

    @Transactional

    public void deleteEmployeeAllocation(String organizationId, String employeeId, String year) {

        List<EmployeeLeaveAllocation> allocations = allocationRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);



        List<EmployeeLeaveBalanceConsumption> consumptions = consumptionRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);



        Map<String, Integer> consumedMap = new HashMap<>();

        for (EmployeeLeaveBalanceConsumption c : consumptions) {

            if (c.getLeaveType() != null && c.getConsumedDays() != null && c.getConsumedDays() > 0) {

                String k = c.getLeaveType().toLowerCase().trim();

                consumedMap.put(k, consumedMap.getOrDefault(k, 0) + c.getConsumedDays());

            }

        }



        for (EmployeeLeaveAllocation a : allocations) {

            String ltKey = a.getLeaveType() != null ? a.getLeaveType().toLowerCase().trim() : "";

            int consumed = consumedMap.getOrDefault(ltKey, a.getConsumedDays() != null ? a.getConsumedDays() : 0);

            if (consumed > 0) {

                throw new RuntimeException("Cannot delete allocation for " + a.getLeaveType() +

                        " because " + consumed + " day(s) have already been consumed.");

            }

        }



        allocationRepository.deleteByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);

    }



    @Override

    @Transactional

    public void deleteSingleLeaveTypeAllocation(String organizationId, String employeeId, String leaveType, String year) {

        EmployeeLeaveAllocation allocation = allocationRepository

                .findByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(organizationId, employeeId, leaveType, year)

                .orElseThrow(() -> new RuntimeException("Allocation not found for leave type: " + leaveType));



        List<EmployeeLeaveBalanceConsumption> consList = consumptionRepository

                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year)

                .stream()

                .filter(c -> c.getLeaveType() != null && c.getLeaveType().equalsIgnoreCase(leaveType))

                .toList();



        int consumed = consList.stream()

                .mapToInt(c -> c.getConsumedDays() != null ? c.getConsumedDays() : 0)

                .sum();

        if (consumed == 0 && allocation.getConsumedDays() != null) {

            consumed = allocation.getConsumedDays();

        }



        if (consumed > 0) {

            throw new RuntimeException("Cannot delete allocation for " + leaveType +

                    " because " + consumed + " day(s) have already been consumed.");

        }



        allocationRepository.delete(allocation);

    }


    @Override
    @Transactional
    public LeaveAllocationImportResultDTO importNewAllocations(String organizationId, MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is empty or missing.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "upload.xlsx";
        }
        String lowerName = filename.toLowerCase();

        // 1. Preload active employees for fast lookup
        List<BasicDetails> activeEmployees = basicDetailsRepository
                .findByOrganization_OrganizationIdAndIsDeletedFalse(organizationId);
        if (activeEmployees == null || activeEmployees.isEmpty()) {
            activeEmployees = basicDetailsRepository.findByOrganization_OrganizationId(organizationId);
        }

        Map<String, BasicDetails> empById = new HashMap<>();
        Map<String, BasicDetails> empByNum = new HashMap<>();
        if (activeEmployees != null) {
            for (BasicDetails emp : activeEmployees) {
                if (emp.getEmployeeId() != null) {
                    empById.put(emp.getEmployeeId().trim().toLowerCase(), emp);
                }
                if (emp.getEmployeeNumber() != null) {
                    empByNum.put(emp.getEmployeeNumber().trim().toLowerCase(), emp);
                }
            }
        }

        // 2. Preload existing allocations for fast duplicate checks
        List<EmployeeLeaveAllocation> existingAllocs = allocationRepository.findByOrganizationId(organizationId);
        Map<String, EmployeeLeaveAllocation> existingAllocMap = new HashMap<>();
        if (existingAllocs != null) {
            for (EmployeeLeaveAllocation a : existingAllocs) {
                if (a.getEmployeeId() != null && a.getLeaveType() != null && a.getYear() != null) {
                    String k = a.getEmployeeId().trim().toLowerCase() + "###" +
                               a.getLeaveType().trim().toLowerCase() + "###" +
                               a.getYear().trim();
                    existingAllocMap.put(k, a);
                }
            }
        }

        List<ImportRowErrorDTO> errors = new ArrayList<>();
        List<EmployeeLeaveAllocation> toSave = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        int[] rowCounter = new int[2]; // [0] = totalRowsProcessed, [1] = successRowsCount

        if (lowerName.endsWith(".csv")) {
            parseRowBasedCsv(file, organizationId, empById, empByNum, existingAllocMap, seenInFile, toSave, errors, createdBy, rowCounter);
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            parseRowBasedExcel(file, organizationId, empById, empByNum, existingAllocMap, seenInFile, toSave, errors, createdBy, rowCounter);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Please upload an Excel (.xlsx, .xls) or CSV (.csv) file.");
        }

        // Batch save all valid allocations
        if (!toSave.isEmpty()) {
            allocationRepository.saveAll(toSave);
        }

        int totalRows = rowCounter[0];
        int successCount = toSave.size();
        int failureCount = errors.size();

        return new LeaveAllocationImportResultDTO(totalRows, successCount, failureCount, errors);
    }

    private static class RowParserContext {
        String currentEmpId = null;
        String currentYear = null;
        LocalDate currentExpDate = null;
        Boolean currentCarryForward = null;
    }

    private void parseRowBasedExcel(MultipartFile file,
                                    String organizationId,
                                    Map<String, BasicDetails> empById,
                                    Map<String, BasicDetails> empByNum,
                                    Map<String, EmployeeLeaveAllocation> existingAllocMap,
                                    Set<String> seenInFile,
                                    List<EmployeeLeaveAllocation> toSave,
                                    List<ImportRowErrorDTO> errors,
                                    String createdBy,
                                    int[] rowCounter) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                return;
            }

            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return;

            int empIdCol = -1;
            int yearCol = -1;
            int leaveTypeCol = -1;
            int annualDaysCol = -1;
            int expDateCol = -1;
            int carryForwardCol = -1;

            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (header.contains("employeeid") || header.contains("empid") || header.contains("employeenumber") || header.contains("employeeno")) {
                    empIdCol = cell.getColumnIndex();
                } else if (header.equals("year")) {
                    yearCol = cell.getColumnIndex();
                } else if (header.contains("leavetype") || header.equals("leave")) {
                    leaveTypeCol = cell.getColumnIndex();
                } else if (header.contains("anualdays") || header.contains("annualdays") || header.contains("anual") || header.contains("annual") || header.contains("days")) {
                    annualDaysCol = cell.getColumnIndex();
                } else if (header.contains("expairation") || header.contains("expiration") || header.contains("expiry")) {
                    expDateCol = cell.getColumnIndex();
                } else if (header.contains("carryforword") || header.contains("carryforward") || header.contains("carry")) {
                    carryForwardCol = cell.getColumnIndex();
                }
            }

            // Defaults if headers not detected
            if (empIdCol == -1) empIdCol = 0;
            if (yearCol == -1) yearCol = 1;
            if (leaveTypeCol == -1) leaveTypeCol = 2;
            if (annualDaysCol == -1) annualDaysCol = 3;
            if (expDateCol == -1) expDateCol = 4;
            if (carryForwardCol == -1) carryForwardCol = 5;

            RowParserContext ctx = new RowParserContext();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                boolean allBlank = true;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                        allBlank = false;
                        break;
                    }
                }
                if (allBlank) continue;

                rowCounter[0]++;
                int rowNum = r + 1;

                String rawEmpId = getSafeCellValue(row.getCell(empIdCol), formatter);
                String rawYear = getSafeCellValue(row.getCell(yearCol), formatter);
                String rawLeaveType = getSafeCellValue(row.getCell(leaveTypeCol), formatter);
                String rawAnnualDays = getSafeCellValue(row.getCell(annualDaysCol), formatter);
                Cell expDateCell = (expDateCol >= 0 && expDateCol < row.getLastCellNum()) ? row.getCell(expDateCol) : null;
                String rawCarryForward = (carryForwardCol >= 0 && carryForwardCol < row.getLastCellNum())
                        ? getSafeCellValue(row.getCell(carryForwardCol), formatter) : "";

                processSingleLeaveRow(rowNum, rawEmpId, rawYear, rawLeaveType, rawAnnualDays, expDateCell, null,
                        rawCarryForward, ctx, organizationId, empById, empByNum, existingAllocMap, seenInFile,
                        toSave, errors, createdBy, formatter);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private void parseRowBasedCsv(MultipartFile file,
                                  String organizationId,
                                  Map<String, BasicDetails> empById,
                                  Map<String, BasicDetails> empByNum,
                                  Map<String, EmployeeLeaveAllocation> existingAllocMap,
                                  Set<String> seenInFile,
                                  List<EmployeeLeaveAllocation> toSave,
                                  List<ImportRowErrorDTO> errors,
                                  String createdBy,
                                  int[] rowCounter) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            List<String> headers = splitCsvLine(headerLine);
            int empIdCol = -1;
            int yearCol = -1;
            int leaveTypeCol = -1;
            int annualDaysCol = -1;
            int expDateCol = -1;
            int carryForwardCol = -1;

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i).trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (header.contains("employeeid") || header.contains("empid") || header.contains("employeenumber") || header.contains("employeeno")) {
                    empIdCol = i;
                } else if (header.equals("year")) {
                    yearCol = i;
                } else if (header.contains("leavetype") || header.equals("leave")) {
                    leaveTypeCol = i;
                } else if (header.contains("anualdays") || header.contains("annualdays") || header.contains("anual") || header.contains("annual") || header.contains("days")) {
                    annualDaysCol = i;
                } else if (header.contains("expairation") || header.contains("expiration") || header.contains("expiry")) {
                    expDateCol = i;
                } else if (header.contains("carryforword") || header.contains("carryforward") || header.contains("carry")) {
                    carryForwardCol = i;
                }
            }

            if (empIdCol == -1) empIdCol = 0;
            if (yearCol == -1) yearCol = 1;
            if (leaveTypeCol == -1) leaveTypeCol = 2;
            if (annualDaysCol == -1) annualDaysCol = 3;
            if (expDateCol == -1) expDateCol = 4;
            if (carryForwardCol == -1) carryForwardCol = 5;

            RowParserContext ctx = new RowParserContext();
            String line;
            int rowNum = 1;

            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) continue;

                List<String> cols = splitCsvLine(line);
                boolean allBlank = cols.stream().allMatch(String::isBlank);
                if (allBlank) continue;

                rowCounter[0]++;

                String rawEmpId = empIdCol < cols.size() ? cols.get(empIdCol).trim() : "";
                String rawYear = yearCol < cols.size() ? cols.get(yearCol).trim() : "";
                String rawLeaveType = leaveTypeCol < cols.size() ? cols.get(leaveTypeCol).trim() : "";
                String rawAnnualDays = annualDaysCol < cols.size() ? cols.get(annualDaysCol).trim() : "";
                String rawExpDate = expDateCol < cols.size() ? cols.get(expDateCol).trim() : "";
                String rawCarryForward = carryForwardCol < cols.size() ? cols.get(carryForwardCol).trim() : "";

                processSingleLeaveRow(rowNum, rawEmpId, rawYear, rawLeaveType, rawAnnualDays, null, rawExpDate,
                        rawCarryForward, ctx, organizationId, empById, empByNum, existingAllocMap, seenInFile,
                        toSave, errors, createdBy, null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    private void processSingleLeaveRow(int rowNum,
                                       String rawEmpId,
                                       String rawYear,
                                       String rawLeaveType,
                                       String rawAnnualDays,
                                       Cell expDateCell,
                                       String rawExpDateStr,
                                       String rawCarryForward,
                                       RowParserContext ctx,
                                       String organizationId,
                                       Map<String, BasicDetails> empById,
                                       Map<String, BasicDetails> empByNum,
                                       Map<String, EmployeeLeaveAllocation> existingAllocMap,
                                       Set<String> seenInFile,
                                       List<EmployeeLeaveAllocation> toSave,
                                       List<ImportRowErrorDTO> errors,
                                       String createdBy,
                                       DataFormatter formatter) {

        // 1. Employee ID (with forward inheritance if blank)
        if (rawEmpId != null && !rawEmpId.isBlank()) {
            ctx.currentEmpId = rawEmpId.trim();
        }
        if (ctx.currentEmpId == null || ctx.currentEmpId.isBlank()) {
            errors.add(new ImportRowErrorDTO(rowNum, "", rawYear, rawLeaveType, "Employee ID is missing."));
            return;
        }

        BasicDetails emp = empById.get(ctx.currentEmpId.toLowerCase());
        if (emp == null) {
            emp = empByNum.get(ctx.currentEmpId.toLowerCase());
        }
        if (emp == null) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, rawYear, rawLeaveType, "Employee ID '" + ctx.currentEmpId + "' does not exist in this organization."));
            return;
        }
        String canonicalEmpId = emp.getEmployeeId();

        // 2. Year (with forward inheritance if blank)
        if (rawYear != null && !rawYear.isBlank()) {
            ctx.currentYear = rawYear.trim();
        }
        if (ctx.currentYear == null || ctx.currentYear.isBlank()) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, "", rawLeaveType, "Year is missing."));
            return;
        }

        int parsedYear;
        try {
            String cleanYear = ctx.currentYear.contains(".") ? ctx.currentYear.substring(0, ctx.currentYear.indexOf('.')) : ctx.currentYear;
            parsedYear = Integer.parseInt(cleanYear.trim());
            if (parsedYear < 2000 || parsedYear > 2100) {
                throw new NumberFormatException();
            }
        } catch (Exception ex) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, ctx.currentYear, rawLeaveType, "Invalid Year '" + ctx.currentYear + "'. Must be a 4-digit year (e.g. 2026)."));
            return;
        }
        String yearStr = String.valueOf(parsedYear);

        // 3. Leave Type
        if (rawLeaveType == null || rawLeaveType.isBlank()) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, "", "Leave Type cannot be blank."));
            return;
        }
        String cleanLeaveType = rawLeaveType.trim();

        // 4. Annual Days
        if (rawAnnualDays == null || rawAnnualDays.isBlank()) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Anual Days cannot be blank."));
            return;
        }

        int annualDays;
        try {
            String cleanDays = rawAnnualDays.contains(".") ? rawAnnualDays.substring(0, rawAnnualDays.indexOf('.')) : rawAnnualDays;
            annualDays = Integer.parseInt(cleanDays.trim());
            if (annualDays <= 0) {
                errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Anual Days must be a positive integer greater than 0."));
                return;
            }
        } catch (Exception ex) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Invalid Anual Days '" + rawAnnualDays + "'. Must be a valid positive integer."));
            return;
        }

        // 5. Expiration Date (Mandatory, not in past, must fall within Year, forward inherited if blank)
        LocalDate expDate = null;
        if (expDateCell != null) {
            if (expDateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(expDateCell)) {
                expDate = expDateCell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else {
                String str = formatter != null ? formatter.formatCellValue(expDateCell).trim() : "";
                if (!str.isBlank()) {
                    expDate = parseLocalDate(str);
                }
            }
        } else if (rawExpDateStr != null && !rawExpDateStr.isBlank()) {
            expDate = parseLocalDate(rawExpDateStr.trim());
        }

        if (expDate != null) {
            ctx.currentExpDate = expDate;
        } else if (ctx.currentExpDate != null) {
            expDate = ctx.currentExpDate;
        }

        if (expDate == null) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Expairation Date is mandatory and must be a valid date (DD-MM-YYYY or YYYY-MM-DD)."));
            return;
        }

        if (expDate.isBefore(LocalDate.now())) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Expairation Date cannot be in the past (" + expDate + ")."));
            return;
        }

        if (expDate.getYear() != parsedYear) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Expairation Date (" + expDate + ") must fall within the allocation year " + parsedYear + "."));
            return;
        }

        // 6. Carry Forward (with forward inheritance if blank)
        if (rawCarryForward != null && !rawCarryForward.isBlank()) {
            String cf = rawCarryForward.trim().toLowerCase();
            ctx.currentCarryForward = "yes".equals(cf) || "true".equals(cf) || "1".equals(cf) || "y".equals(cf);
        }
        boolean carryForward = ctx.currentCarryForward != null ? ctx.currentCarryForward : false;

        // 7. Check Duplicate in File
        String uniqueKey = canonicalEmpId.toLowerCase() + "###" + cleanLeaveType.toLowerCase() + "###" + yearStr;
        if (seenInFile.contains(uniqueKey)) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Duplicate record found in file for employee '" + ctx.currentEmpId + "', leave type '" + cleanLeaveType + "', and year " + yearStr + "."));
            return;
        }
        seenInFile.add(uniqueKey);

        // 8. Check Duplicate in Database
        if (existingAllocMap.containsKey(uniqueKey)) {
            errors.add(new ImportRowErrorDTO(rowNum, ctx.currentEmpId, yearStr, cleanLeaveType, "Employee '" + ctx.currentEmpId + "' already has an allocation for '" + cleanLeaveType + "' in year " + yearStr + ". Cannot insert duplicate record."));
            return;
        }

        // 9. Build Entity
        EmployeeLeaveAllocation alloc = new EmployeeLeaveAllocation();
        alloc.setOrganizationId(organizationId);
        alloc.setEmployeeId(canonicalEmpId);
        alloc.setLeaveType(cleanLeaveType);
        alloc.setYear(yearStr);
        alloc.setAnnualDays(annualDays);
        alloc.setCarriedForwardDays(0);
        alloc.setConsumedDays(0);
        alloc.setLopDays(0);
        alloc.setLwp(0);
        alloc.setExpirationDate(expDate);
        alloc.setCarryForward(carryForward);
        alloc.setCreatedBy(createdBy != null ? createdBy : "Import");
        alloc.setCreatedAt(LocalDateTime.now());
        alloc.setUpdatedAt(LocalDateTime.now());

        toSave.add(alloc);
        existingAllocMap.put(uniqueKey, alloc);
    }

    private String getSafeCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseLocalDate(String str) {
        if (str == null || str.isBlank()) return null;
        String s = str.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                result.add(curVal.toString().trim());
                curVal.setLength(0);
            } else {
                curVal.append(ch);
            }
        }
        result.add(curVal.toString().trim());
        return result;
    }
}

