package com.itsdev.payroll.serviceimpl.leave;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.leave.EmployeeLeaveConsumptionResponseDTO;
import com.itsdev.payroll.dto.leave.LeaveAllocationItemDTO;
import com.itsdev.payroll.dto.leave.LeaveEntryDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.leave.EmployeeLeaveAllocation;
import com.itsdev.payroll.entity.leave.EmployeeLeaveBalanceConsumption;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.leave.EmployeeLeaveAllocationRepository;
import com.itsdev.payroll.repository.leave.EmployeeLeaveBalanceConsumptionRepository;
import com.itsdev.payroll.service.leave.EmployeeLeaveConsumptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeLeaveConsumptionServiceImpl implements EmployeeLeaveConsumptionService {

    private final EmployeeLeaveBalanceConsumptionRepository consumptionRepository;
    private final EmployeeLeaveAllocationRepository allocationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final ObjectMapper objectMapper;

    public EmployeeLeaveConsumptionServiceImpl(
            EmployeeLeaveBalanceConsumptionRepository consumptionRepository,
            EmployeeLeaveAllocationRepository allocationRepository,
            BasicDetailsRepository basicDetailsRepository,
            ObjectMapper objectMapper) {
        this.consumptionRepository = consumptionRepository;
        this.allocationRepository = allocationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmployeeLeaveConsumptionResponseDTO updateEmployeeConsumption(
            String organizationId,
            String employeeId,
            String year,
            String leaveMonth,
            List<LeaveAllocationItemDTO> leaveTypes,
            String updatedBy) {

        if (leaveTypes == null || leaveTypes.isEmpty()) {
            return getEmployeeConsumption(organizationId, employeeId, year);
        }

        // 1. Fetch allocations to get quota
        List<EmployeeLeaveAllocation> allocations = allocationRepository
                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);
        Map<String, EmployeeLeaveAllocation> allocMap = allocations.stream()
                .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (k1, k2) -> k1));

        // 2. Process incoming leave entries
        for (LeaveAllocationItemDTO item : leaveTypes) {
            String lt = item.getLeaveType();
            if (lt == null || lt.isBlank()) continue;

            EmployeeLeaveAllocation alloc = allocMap.get(lt.toLowerCase());
            Long allocId = alloc != null ? alloc.getId() : null;

            // Check if monthlyEntries is provided (granular entries array)
            if (item.getMonthlyEntries() != null && !item.getMonthlyEntries().isEmpty()) {
                for (Map.Entry<String, List<LeaveEntryDTO>> mEntry : item.getMonthlyEntries().entrySet()) {
                    String monthKey = mEntry.getKey();
                    List<LeaveEntryDTO> incomingList = mEntry.getValue();
                    if (incomingList == null) continue;

                    for (LeaveEntryDTO inE : incomingList) {
                        String eId = inE.getLeaveId();
                        EmployeeLeaveBalanceConsumption entity = null;
                        if (eId != null && !eId.isBlank()) {
                            entity = consumptionRepository.findByLeaveId(eId).orElse(null);
                        }

                        if (entity == null) {
                            entity = new EmployeeLeaveBalanceConsumption();
                            entity.setLeaveId((eId != null && !eId.isBlank()) ? eId : "LV-" + year + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
                            entity.setOrganizationId(organizationId);
                            entity.setEmployeeId(employeeId);
                            entity.setLeaveType(lt);
                            entity.setYear(year);
                            entity.setAllocationId(allocId);
                            entity.setCreatedAt(LocalDateTime.now());
                            entity.setCreatedBy(updatedBy);
                        }

                        int days = (inE.getDaysTaken() != null) ? inE.getDaysTaken() : 0;
                        entity.setConsumedDays(days);
                        entity.setReason(inE.getReason());
                        entity.setLeaveMonth(monthKey != null ? monthKey : leaveMonth);
                        entity.setLwp(inE.getLwp() != null ? inE.getLwp() : 0);
                        entity.setUpdatedAt(LocalDateTime.now());

                        consumptionRepository.save(entity);
                    }
                }
            } else if ((item.getConsumedDays() != null && item.getConsumedDays() > 0) || (item.getLwp() != null && item.getLwp() > 0)) {
                // Single-entry submission
                EmployeeLeaveBalanceConsumption entity = new EmployeeLeaveBalanceConsumption();
                entity.setLeaveId("LV-" + year + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
                entity.setOrganizationId(organizationId);
                entity.setEmployeeId(employeeId);
                entity.setLeaveType(lt);
                entity.setYear(year);
                entity.setAllocationId(allocId);
                entity.setConsumedDays(item.getConsumedDays());
                entity.setReason(item.getReason());
                entity.setLeaveMonth(leaveMonth);
                entity.setLwp(item.getLwp() != null ? item.getLwp() : 0);
                entity.setCreatedBy(updatedBy);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());

                consumptionRepository.save(entity);
            }
        }

        // 3. Recalculate Point-in-Time running balances for all rows of this employee
        recalculateRunningBalances(organizationId, employeeId, year, allocMap);

        return getEmployeeConsumption(organizationId, employeeId, year);
    }

    /**
     * Recalculates point-in-time running_ytd and balance_after in chronological order
     */
    private void recalculateRunningBalances(
            String organizationId,
            String employeeId,
            String year,
            Map<String, EmployeeLeaveAllocation> allocMap) {

        List<EmployeeLeaveBalanceConsumption> rows =
                consumptionRepository.findByOrganizationIdAndEmployeeIdAndYearOrderByIdAsc(organizationId, employeeId, year);

        Map<String, Integer> runningYtdMap = new HashMap<>();

        for (EmployeeLeaveBalanceConsumption row : rows) {
            String ltKey = row.getLeaveType().toLowerCase();
            EmployeeLeaveAllocation alloc = allocMap.get(ltKey);
            int totalAlloc = (alloc != null ? alloc.getAnnualDays() + alloc.getCarriedForwardDays() : 12);

            int prevYtd = runningYtdMap.getOrDefault(ltKey, 0);
            int currentYtd = prevYtd + row.getConsumedDays();
            runningYtdMap.put(ltKey, currentYtd);

            int balanceAfter = Math.max(0, totalAlloc - currentYtd);
            int prevLop = prevYtd > totalAlloc ? (prevYtd - totalAlloc) : 0;
            int currLop = currentYtd > totalAlloc ? (currentYtd - totalAlloc) : 0;
            int incrementalLop = currLop - prevLop;

            row.setRunningYtd(currentYtd);
            row.setBalanceAfter(balanceAfter);
            row.setBalanceDays(balanceAfter);
            row.setLopDays(incrementalLop);
            row.setUpdatedAt(LocalDateTime.now());

            consumptionRepository.save(row);
        }
    }

    @Override
    public List<EmployeeLeaveConsumptionResponseDTO> getConsumptions(String organizationId, String year) {
        List<EmployeeLeaveBalanceConsumption> consumptions = (year != null && !year.isBlank())
                ? consumptionRepository.findByOrganizationIdAndYear(organizationId, year)
                : consumptionRepository.findByOrganizationId(organizationId);

        List<EmployeeLeaveAllocation> allocations = (year != null && !year.isBlank())
                ? allocationRepository.findByOrganizationIdAndYear(organizationId, year)
                : allocationRepository.findByOrganizationId(organizationId);

        Set<String> employeeIds = new LinkedHashSet<>();
        for (EmployeeLeaveAllocation a : allocations) {
            employeeIds.add(a.getEmployeeId());
        }
        for (EmployeeLeaveBalanceConsumption c : consumptions) {
            employeeIds.add(c.getEmployeeId());
        }

        Map<String, List<EmployeeLeaveBalanceConsumption>> consByEmp =
                consumptions.stream().collect(Collectors.groupingBy(EmployeeLeaveBalanceConsumption::getEmployeeId));

        Map<String, List<EmployeeLeaveAllocation>> allocByEmp =
                allocations.stream().collect(Collectors.groupingBy(EmployeeLeaveAllocation::getEmployeeId));

        List<EmployeeLeaveConsumptionResponseDTO> result = new ArrayList<>();
        for (String empId : employeeIds) {
            List<EmployeeLeaveBalanceConsumption> empCons = consByEmp.getOrDefault(empId, Collections.emptyList());
            List<EmployeeLeaveAllocation> empAlloc = allocByEmp.getOrDefault(empId, Collections.emptyList());

            Map<String, EmployeeLeaveAllocation> allocMap = empAlloc.stream()
                    .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (k1, k2) -> k1));

            result.add(buildEmployeeConsumptionDTO(organizationId, empId, year, empCons, allocMap));
        }

        return result;
    }

    @Override
    public EmployeeLeaveConsumptionResponseDTO getEmployeeConsumption(String organizationId, String employeeId, String year) {
        List<EmployeeLeaveBalanceConsumption> consumptions =
                consumptionRepository.findByOrganizationIdAndEmployeeIdAndYearOrderByIdAsc(organizationId, employeeId, year);

        List<EmployeeLeaveAllocation> allocations =
                allocationRepository.findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);

        Map<String, EmployeeLeaveAllocation> allocMap = allocations.stream()
                .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (k1, k2) -> k1));

        return buildEmployeeConsumptionDTO(organizationId, employeeId, year, consumptions, allocMap);
    }

    @Override
    public void deleteEmployeeConsumption(String organizationId, String employeeId, String year) {
        consumptionRepository.deleteByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);
    }

    @Override
    public void deleteEmployeeMonthConsumption(String organizationId, String employeeId, String year, String month) {
        List<EmployeeLeaveBalanceConsumption> rows =
                consumptionRepository.findByOrganizationIdAndEmployeeIdAndYearOrderByIdAsc(organizationId, employeeId, year);
        for (EmployeeLeaveBalanceConsumption row : rows) {
            if (month.equalsIgnoreCase(row.getLeaveMonth())) {
                consumptionRepository.delete(row);
            }
        }
        List<EmployeeLeaveAllocation> allocations = allocationRepository
                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);
        Map<String, EmployeeLeaveAllocation> allocMap = allocations.stream()
                .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (k1, k2) -> k1));
        recalculateRunningBalances(organizationId, employeeId, year, allocMap);
    }

    @Override
    public void deleteEmployeeEntry(String organizationId, String employeeId, String year, String entryId) {
        Optional<EmployeeLeaveBalanceConsumption> opt = consumptionRepository.findByLeaveId(entryId);
        if (opt.isPresent()) {
            consumptionRepository.delete(opt.get());
        } else {
            // Try by ID
            try {
                Long id = Long.parseLong(entryId);
                consumptionRepository.deleteById(id);
            } catch (Exception ignored) {}
        }

        List<EmployeeLeaveAllocation> allocations = allocationRepository
                .findByOrganizationIdAndEmployeeIdAndYear(organizationId, employeeId, year);
        Map<String, EmployeeLeaveAllocation> allocMap = allocations.stream()
                .collect(Collectors.toMap(a -> a.getLeaveType().toLowerCase(), a -> a, (k1, k2) -> k1));
        recalculateRunningBalances(organizationId, employeeId, year, allocMap);
    }

    @Override
    public void deleteSingleLeaveTypeConsumption(String organizationId, String employeeId, String leaveType, String year) {
        consumptionRepository.deleteByOrganizationIdAndEmployeeIdAndLeaveTypeAndYear(organizationId, employeeId, leaveType, year);
    }

    private EmployeeLeaveConsumptionResponseDTO buildEmployeeConsumptionDTO(
            String organizationId,
            String employeeId,
            String year,
            List<EmployeeLeaveBalanceConsumption> consumptions,
            Map<String, EmployeeLeaveAllocation> allocMap) {

        EmployeeLeaveConsumptionResponseDTO dto = new EmployeeLeaveConsumptionResponseDTO();
        dto.setEmployeeId(employeeId);
        dto.setYear(year);

        BasicDetails emp = basicDetailsRepository.findByEmployeeId(employeeId).orElse(null);
        if (emp != null) {
            dto.setEmployeeNumber(emp.getEmployeeNumber());
            String fullName = ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " +
                    (emp.getLastName() != null ? emp.getLastName() : "")).trim();
            dto.setEmployeeName(fullName.isEmpty() ? "Employee" : fullName);
        } else {
            dto.setEmployeeNumber("EMP");
            dto.setEmployeeName("Employee");
        }

        int totalAlloc = 0;
        int totalConsumed = 0;
        int totalRemaining = 0;
        int totalLop = 0;
        int totalLwp = 0;
        String latestMonth = null;
        LocalDateTime latestUpdate = null;
        LocalDateTime earliestCreate = null;

        Map<String, List<EmployeeLeaveBalanceConsumption>> consByType = (consumptions != null)
                ? consumptions.stream().collect(Collectors.groupingBy(c -> c.getLeaveType().toLowerCase()))
                : Collections.emptyMap();

        Set<String> allLeaveTypes = new LinkedHashSet<>();
        if (allocMap != null) {
            for (EmployeeLeaveAllocation a : allocMap.values()) {
                allLeaveTypes.add(a.getLeaveType());
            }
        }
        if (consumptions != null) {
            for (EmployeeLeaveBalanceConsumption c : consumptions) {
                allLeaveTypes.add(c.getLeaveType());
            }
        }

        List<LeaveAllocationItemDTO> items = new ArrayList<>();
        for (String ltName : allLeaveTypes) {
            LeaveAllocationItemDTO item = new LeaveAllocationItemDTO();
            item.setLeaveType(ltName);

            EmployeeLeaveAllocation alloc = (allocMap != null) ? allocMap.get(ltName.toLowerCase()) : null;
            List<EmployeeLeaveBalanceConsumption> typeRows = consByType.getOrDefault(ltName.toLowerCase(), Collections.emptyList());

            int annual = alloc != null ? alloc.getAnnualDays() : 12;
            int carried = alloc != null ? alloc.getCarriedForwardDays() : 0;
            int total = annual + carried;

            item.setAnnualDays(annual);
            item.setCarriedForwardDays(carried);
            item.setTotalDays(total);

            int typeConsumed = typeRows.stream().mapToInt(EmployeeLeaveBalanceConsumption::getConsumedDays).sum();
            int typeLop = typeRows.stream().mapToInt(EmployeeLeaveBalanceConsumption::getLopDays).sum();
            int typeLwp = typeRows.stream().mapToInt(EmployeeLeaveBalanceConsumption::getLwp).sum();
            int remaining = Math.max(0, total - typeConsumed);

            item.setConsumedDays(typeConsumed);
            item.setBalanceDays(remaining);
            item.setRemainingDays(remaining);
            item.setLopDays(typeLop);
            item.setLwp(typeLwp);

            Map<String, List<LeaveEntryDTO>> entriesMap = new LinkedHashMap<>();
            Map<String, Integer> monthlyBreakdown = new LinkedHashMap<>();
            Map<String, Integer> monthlyLopBreakdown = new LinkedHashMap<>();
            Map<String, Integer> monthlyLwpBreakdown = new LinkedHashMap<>();

            for (EmployeeLeaveBalanceConsumption row : typeRows) {
                String m = row.getLeaveMonth() != null ? row.getLeaveMonth() : ("September " + year);
                latestMonth = m;

                LeaveEntryDTO entryDTO = new LeaveEntryDTO();
                entryDTO.setEntryId(row.getLeaveId() != null ? row.getLeaveId() : String.valueOf(row.getId()));
                entryDTO.setLeaveId(row.getLeaveId() != null ? row.getLeaveId() : String.valueOf(row.getId()));
                entryDTO.setLeaveType(row.getLeaveType());
                entryDTO.setDaysTaken(row.getConsumedDays());
                entryDTO.setBalanceAfter(row.getBalanceAfter() != null ? row.getBalanceAfter() : remaining);
                entryDTO.setRunningYtd(row.getRunningYtd() != null ? row.getRunningYtd() : typeConsumed);
                entryDTO.setLeaveMonth(m);
                entryDTO.setReason(row.getReason());
                entryDTO.setLopDays(row.getLopDays());
                entryDTO.setLwp(row.getLwp());
                entryDTO.setMarkedBy(row.getCreatedBy());
                entryDTO.setMarkedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : LocalDateTime.now().toString());

                entriesMap.computeIfAbsent(m, k -> new ArrayList<>()).add(entryDTO);

                monthlyBreakdown.put(m, monthlyBreakdown.getOrDefault(m, 0) + row.getConsumedDays());
                monthlyLopBreakdown.put(m, monthlyLopBreakdown.getOrDefault(m, 0) + row.getLopDays());
                monthlyLwpBreakdown.put(m, monthlyLwpBreakdown.getOrDefault(m, 0) + row.getLwp());

                if (latestUpdate == null || (row.getUpdatedAt() != null && row.getUpdatedAt().isAfter(latestUpdate))) {
                    latestUpdate = row.getUpdatedAt();
                }
                if (earliestCreate == null || (row.getCreatedAt() != null && row.getCreatedAt().isBefore(earliestCreate))) {
                    earliestCreate = row.getCreatedAt();
                }
            }

            item.setMonthlyEntries(entriesMap);
            item.setMonthlyBreakdown(monthlyBreakdown);
            item.setMonthlyLopBreakdown(monthlyLopBreakdown);
            item.setMonthlyLwpBreakdown(monthlyLwpBreakdown);
            item.setLeaveMonth(latestMonth);

            totalAlloc += total;
            totalConsumed += typeConsumed;
            totalRemaining += remaining;
            totalLop += typeLop;
            totalLwp += typeLwp;

            items.add(item);
        }

        dto.setTotalAllocatedDays(totalAlloc);
        dto.setTotalConsumedDays(totalConsumed);
        dto.setTotalRemainingDays(totalRemaining);
        dto.setTotalLopDays(totalLop);
        dto.setTotalLwp(totalLwp);
        dto.setLeaveMonth(latestMonth);
        dto.setCreatedAt(earliestCreate != null ? earliestCreate : LocalDateTime.now());
        dto.setUpdatedAt(latestUpdate != null ? latestUpdate : LocalDateTime.now());
        dto.setLeaveTypes(items);

        return dto;
    }
}
