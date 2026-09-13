
package com.phegondev.usersmanagementsystem.serviceimpl.leaverequest;


import com.phegondev.usersmanagementsystem.dto.CloudinaryUploadResponseDto;
import com.phegondev.usersmanagementsystem.dto.LeaveRequestsDTO;
import com.phegondev.usersmanagementsystem.entity.*;
import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import com.phegondev.usersmanagementsystem.repository.EmployeeLeaveBalanceRepo;
import com.phegondev.usersmanagementsystem.repository.EmployeeMonthlyLopRepository;
import com.phegondev.usersmanagementsystem.repository.LeaveTypeRepository;
import com.phegondev.usersmanagementsystem.repository.leaverequest.LeaveRequestRepo;
import com.phegondev.usersmanagementsystem.service.CloudinaryServiceImpl;
import com.phegondev.usersmanagementsystem.service.EmployeeService;
import com.phegondev.usersmanagementsystem.service.leaverequest.LeaveRequestsService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.phegondev.usersmanagementsystem.dto.LeaveDistributionDTO;


@Service
public class LeaveRequestServiceImpl implements LeaveRequestsService {

    private final LeaveTypeRepository leaveTypeRepo;
    private final EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final EmployeeService employeeService;
    private  final CloudinaryServiceImpl cloudinaryService;
    private final EmployeeMonthlyLopRepository employeeMonthlyLopRepository;

    private static final Logger logger = LoggerFactory.getLogger(LeaveRequestServiceImpl.class);


    // Constructor Injection
    public LeaveRequestServiceImpl(
            LeaveTypeRepository leaveTypeRepo,
            EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo,
            LeaveRequestRepo leaveRequestRepo, EmployeeService employeeService,
            CloudinaryServiceImpl cloudinaryService,
            EmployeeMonthlyLopRepository employeeMonthlyLopRepository) {
        this.leaveTypeRepo = leaveTypeRepo;
        this.employeeLeaveBalanceRepo = employeeLeaveBalanceRepo;
        this.leaveRequestRepo = leaveRequestRepo;
        this.employeeService = employeeService;
        this.cloudinaryService = cloudinaryService;
        this.employeeMonthlyLopRepository = employeeMonthlyLopRepository;
    }


    private Map<String, LeaveType> buildLeaveTypeMap(List<LeaveType> types) {
        Map<String, LeaveType> map = new HashMap<>();
        if (types == null) return map;

        for (LeaveType t : types) {
            if (t == null) continue;
            // map by id (stringified)
            if (t.getId() != null) {
                map.put(String.valueOf(t.getId()), t);
            }
            // map by name (human readable)
            if (t.getName() != null) {
                map.put(t.getName(), t);
            }
        }
        return map;
    }




    /**
     * Convert entity's Map<Long, Float> manualDaysAllocation into Map<String, Double>
     */
    private Map<String, Double> parseManualDaysAllocation(LeaveRequests leave) {
        if (leave == null) return Collections.emptyMap();

        try {
            Map<Long, Float> raw = leave.getManualDaysAllocation(); // entity getter
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, Double> result = new LinkedHashMap<>(); // preserves order
            for (Map.Entry<Long, Float> e : raw.entrySet()) {
                if (e == null) continue;
                Long key = e.getKey();
                Float value = e.getValue();
                if (key == null) continue;

                double d = (value == null) ? 0.0d : value.doubleValue();
                result.put(String.valueOf(key), d);
            }
            return result;

        } catch (Exception ex) {
            logger.warn("Failed to read manualDaysAllocation for leave id {}: {}", leave.getId(), ex.getMessage());
            return Collections.emptyMap();
        }
    }



    /**
     * Enrich a LeaveRequestsDTO with leaveDistribution and manualDaysAllocationByName
     * using the provided typeMap (stringified id -> LeaveType).
     */
    private void enrichDtoWithDistribution(LeaveRequests entity, LeaveRequestsDTO dto, Map<String, LeaveType> typeMap) {
        if (entity == null || dto == null) return;

        // parse manual allocation -> Map<String, Double>
        Map<String, Double> manual = parseManualDaysAllocation(entity);

        // Build distribution list
        List<LeaveDistributionDTO> distribution = new ArrayList<>();
        Map<String, Double> manualByName = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : manual.entrySet()) {
            String typeKey = String.valueOf(entry.getKey()); // e.g. "6"
            Double days = entry.getValue() == null ? 0.0d : entry.getValue();

            LeaveType lt = typeMap.get(typeKey);
            String resolvedName = (lt != null && lt.getName() != null) ? lt.getName() : "Type " + typeKey;

            LeaveDistributionDTO d = new LeaveDistributionDTO(typeKey, resolvedName, days);
            distribution.add(d);
            manualByName.put(resolvedName, days);
        }

        dto.setLeaveDistribution(distribution);
        dto.setManualDaysAllocationByName(manualByName);
    }





    @Override
    public LeaveRequests saveLeaveRequest(LeaveRequestsDTO dto, MultipartFile file){
        System.out.println("[INFO] Starting leave request save process for employee ID: " + dto.getEmployeeId());

        if (dto.getToDate().isBefore(dto.getFromDate())) {
            System.out.println("[ERROR] To date is before From date.");
            throw new IllegalArgumentException("To date cannot be before From date.");
        }

        double daysRequested = dto.getTotalDays();
        System.out.println("[INFO] Total leave days requested: " + daysRequested);

        if (!isMultipleOfHalf(daysRequested)) {
            System.out.println("[ERROR] Total leave days must be in multiples of 0.5: " + daysRequested);
            throw new IllegalArgumentException("Total leave days must be in 0.5 increments (e.g., 0.5, 1.0, 1.5...).");
        }

        Map<Long, Float> manualDaysAllocation = dto.getManualDaysAllocation();
        if (manualDaysAllocation == null || manualDaysAllocation.isEmpty()) {
            System.out.println("[ERROR] Manual leave day allocation is missing.");
            throw new IllegalArgumentException("Manual leave allocation per leave type is required.");
        }

        System.out.println("[INFO] Manual leave type allocations: " + manualDaysAllocation);

        Map<Long, Float> lopAllocation = new HashMap<>();



// ---------------------------------------
// OPTIONAL VALIDATION FOR SELECTED DATES
// ---------------------------------------
        if (dto.getSelectedDates() != null && !dto.getSelectedDates().isEmpty()) {

            // how many days user selected from calendar
            double selCount = dto.getSelectedDates().size() * (dto.getIsHalfDay() ? 0.5 : 1.0);

            // how many days admin/user allocated per leave type
            double sumAllocated = dto.getManualDaysAllocation() != null ?
                    dto.getManualDaysAllocation().values()
                            .stream()
                            .mapToDouble(Float::doubleValue).sum()
                    : 0.0;

            // mismatch? → reject early before any DB updates
            if (Math.abs(sumAllocated - selCount) > 0.0001) {
                throw new IllegalArgumentException(
                        "Sum of manualDaysAllocation (" + sumAllocated +
                                ") must equal number of selected dates (" + selCount + ")"
                );
            }
        }


        for (Map.Entry<Long, Float> entry : manualDaysAllocation.entrySet()) {
            Long leaveTypeId = entry.getKey();
            Float allocatedDays = entry.getValue();

            System.out.println("[INFO] Processing leave type ID: " + leaveTypeId + " with allocated days: " + allocatedDays);

            if (!isMultipleOfHalf(allocatedDays)) {
                System.out.println("[ERROR] Leave days for type ID " + leaveTypeId + " are not in 0.5 multiples.");
                throw new IllegalArgumentException("Leave days must be in 0.5 increments for leave type ID: " + leaveTypeId);
            }

            LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
                    .orElseThrow(() -> {
                        System.out.println("[ERROR] Leave type not found for ID: " + leaveTypeId);
                        return new IllegalArgumentException("Invalid leave type ID: " + leaveTypeId);
                    });

            System.out.println("[INFO] Found leave type: " + leaveType.getName());

            Optional<EmployeeLeaveBalance> balanceOpt = employeeLeaveBalanceRepo
                    .findByEmployeeIdAndLeaveTypeId(dto.getEmployeeId(), leaveTypeId);

            EmployeeLeaveBalance balance = balanceOpt.orElseGet(() -> {
                EmployeeLeaveBalance newBalance = new EmployeeLeaveBalance();
                newBalance.setEmployeeId(dto.getEmployeeId());
                newBalance.setLeaveType(leaveType.getName());
                newBalance.setLeaveTypeId(leaveTypeId);
                newBalance.setRemainingDays((float) leaveType.getDefaultDays());
                // newBalance.setRemainingDays((float) leaveType.getDefaultDays()-allocatedDays);
                System.out.println("[INFO] Created new balance: " + newBalance.getRemainingDays());
                return newBalance;
            });

            if (balanceOpt.isPresent()) {
                System.out.println("[INFO] Existing balance found: " + balance.getRemainingDays());
            }

            float beforeBalance = balance.getRemainingDays();
            float afterBalance = beforeBalance - allocatedDays;
            balance.setRemainingDays(afterBalance);

            System.out.println("[INFO] Updated balance after deduction for leave type ID " + leaveTypeId + ": before="
                    + beforeBalance + ", allocated=" + allocatedDays + ", after=" + afterBalance);

            float lopDays = 0F;
            if (afterBalance < 0) {
                lopDays = Math.abs(afterBalance);
                if (beforeBalance < 0) {
                    lopDays = Math.abs(afterBalance) - Math.abs(beforeBalance);
                }
            }

            System.out.println("[INFO] Calculated LOP days for leave type ID " + leaveTypeId + ": " + lopDays);

            if (lopDays > 0) {
                lopAllocation.put(leaveTypeId, lopDays);
                System.out.println("[INFO] LOP allocated for leave type ID " + leaveTypeId + ": " + lopDays);
            } else {
                System.out.println("[INFO] No LOP required for leave type ID " + leaveTypeId + ".");
            }

            employeeLeaveBalanceRepo.save(balance);
            System.out.println("[INFO] Saved updated balance for leave type: " + leaveType.getName());
        }

        LeaveRequests entity = new LeaveRequests();

        entity.setEmployeeId(dto.getEmployeeId());
        entity.setEmployeeName(dto.getEmployeeName());
        entity.setFromDate(dto.getFromDate());
        entity.setToDate(dto.getToDate());
        entity.setIsHalfDay(dto.getIsHalfDay());
        entity.setHalfDayPeriod(dto.getHalfDayPeriod());
        entity.setReason(dto.getReason());
        entity.setLateReason(dto.getLateReason());
        entity.setTotalDays(dto.getTotalDays());
        entity.setManualDaysAllocation(dto.getManualDaysAllocation());
        entity.setLopAllocation(lopAllocation);

        System.out.println("[INFO] Lop allocation map prepared: " + lopAllocation);


        System.out.println("DTO Selected Dates = " + dto.getSelectedDates());

// map and set selectedDates (if provided)
        if (dto.getSelectedDates() != null && !dto.getSelectedDates().isEmpty()) {
            Set<LocalDate> parsed = dto.getSelectedDates().stream()
                    .filter(Objects::nonNull)
                    .map(LocalDate::parse) // expects "yyyy-MM-dd"
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            entity.setSelectedDates(parsed);
        }


        LeaveRequests saved = leaveRequestRepo.save(entity);
        System.out.println("[SUCCESS] Leave request saved successfully with ID: " + saved.getId());

        String empId = dto.getEmployeeId();

        if (file != null && !file.isEmpty()) {
            System.out.println("[SERVICE] File uploaded. Name: " + file.getOriginalFilename());

            try {
                LeaveDocument doc = new LeaveDocument();
                doc.setLeaveRequest(saved);
                doc.setFileName(file.getOriginalFilename());
                doc.setFileType(file.getContentType());

                //  doc.setFileData(file.getBytes());
                CloudinaryUploadResponseDto responseDto = cloudinaryService.uploadFile(file,empId);
                doc.setFileUrl(responseDto.getSecure_url());
                doc.setPublicId(responseDto.getPublic_id());

                saved.setMedicalCertificate(doc);
                leaveRequestRepo.save(saved); // cascade saves LeaveDocument
                System.out.println("[SERVICE] Medical certificate saved and linked to leave ID: " + saved.getId());

            } catch (IOException e) {
                System.out.println("[ERROR] Failed to save file: " + e.getMessage());
                throw new RuntimeException("File save failed");
            }
        } else {
            System.out.println("[SERVICE] No file uploaded. Skipping file save.");
        }

        return saved;
    }

    private void generateMonthlyLop(LeaveRequests request) {

        System.out.println("[INFO] Generating monthly LOP CALLED for leave request ID: " + request.getId()+" at " +LocalDateTime.now());

        Map<Long, Float> lopAllocation = request.getLopAllocation();

        System.out.println("LOP Allocation = " + request.getLopAllocation());
        System.out.println("Selected Dates = " + request.getSelectedDates());

        if (lopAllocation == null || lopAllocation.isEmpty()) {
            System.out.println("[INFO] No LOP allocation found.");
            return;
        }

        // Generate dates from fromDate and toDate
        List<LocalDate> allDates  = new ArrayList<>();

        LocalDate currentDate = request.getFromDate();

        while (!currentDate.isAfter(request.getToDate())) {

            allDates .add(currentDate);

            currentDate = currentDate.plusDays(1);
        }

        System.out.println("Generated Dates = " + allDates );

        for (Map.Entry<Long, Float> entry : lopAllocation.entrySet()) {

            Long leaveTypeId = entry.getKey();

            Float lopDays = entry.getValue();

            System.out.println("LOP DAYS = " + lopDays);

            if (lopDays == null || lopDays <= 0) {
                continue;
            }

            LeaveType leaveType =
                    leaveTypeRepo.findById(leaveTypeId)
                            .orElse(null);

            if (leaveType == null) {
                continue;
            }

            // Find dates that belong to LOP
            // Taking last dates first
            List<LocalDate> lopDates = new ArrayList<>();

            float remainingLop = lopDays;

            for (int i = allDates.size() - 1; i >= 0 && remainingLop > 0; i--) {

                lopDates.add(0, allDates.get(i));

                remainingLop -= 1f;
            }

            System.out.println("LOP DATES = " + lopDates);

            // Month wise split
            Map<YearMonth, Float> monthlyLop = new LinkedHashMap<>();

            remainingLop = lopDays;

            for (LocalDate date : lopDates) {

                YearMonth ym = YearMonth.from(date);
                float amountToAdd;

                if (remainingLop >= 1f) {
                    amountToAdd = 1f;
                }
                else {
                    amountToAdd = remainingLop;
                }

                monthlyLop.merge(ym, amountToAdd, Float::sum);

                remainingLop -= amountToAdd;

                if (remainingLop <= 0) {
                    break;
                }
            }

            System.out.println("Monthly LOP = " +monthlyLop);

            for (Map.Entry<YearMonth, Float> monthEntry : monthlyLop.entrySet()) {

                YearMonth ym = monthEntry.getKey();

                Float monthLopDays = monthEntry.getValue();

                Optional<EmployeeMonthlyLop> existing =
                        employeeMonthlyLopRepository
                                .findByEmployeeIdAndLeaveTypeIdAndYearAndMonth(
                                        request.getEmployeeId(),
                                        leaveTypeId,
                                        ym.getYear(),
                                        ym.getMonthValue()
                                );

                System.out.println("EXISTING PRESENT = " + existing.isPresent());

                if (existing.isPresent()) {

                    EmployeeMonthlyLop db = existing.get();

                    db.setLopDays(db.getLopDays() + monthLopDays);

                    employeeMonthlyLopRepository.save(db);

                    System.out.println("[UPDATED] Existing monthly LOP updated.");

                } else {

                    EmployeeMonthlyLop lop = new EmployeeMonthlyLop();

                    lop.setEmployeeId(request.getEmployeeId());
                    lop.setLeaveRequestId(request.getId());
                    lop.setLeaveTypeId(leaveTypeId);
                    lop.setLeaveTypeName(leaveType.getName());
                    lop.setYear(ym.getYear());
                    lop.setMonth(ym.getMonthValue());
                    lop.setLopDays(monthLopDays);
                    lop.setPayrollProcessed(false);

                    employeeMonthlyLopRepository.save(lop);

                    System.out.println("[INSERTED] Monthly LOP inserted.");
                }
            }
        }
    }

    @Override
    public List<LeaveRequestsDTO> getLeaveRequestsByEmployeeId(String employeeId) {
        List<LeaveType> types = leaveTypeRepo.findAll();
        Map<String, LeaveType> typeMap = buildLeaveTypeMap(types);

        List<LeaveRequests> requests = leaveRequestRepo.findByEmployeeId(employeeId);
        return requests.stream()
                .map(entity -> {
                    LeaveRequestsDTO dto = convertToDto(entity);
                    enrichDtoWithDistribution(entity, dto, typeMap);
                    return dto;
                })
                .collect(Collectors.toList());
    }


    private LeaveRequestsDTO convertToDto(LeaveRequests entity) {
        LeaveRequestsDTO dto = new LeaveRequestsDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setFromDate(entity.getFromDate());
        dto.setToDate(entity.getToDate());
        dto.setIsHalfDay(entity.getIsHalfDay());
        dto.setHalfDayPeriod(entity.getHalfDayPeriod());
        dto.setReason(entity.getReason());
        dto.setLateReason(entity.getLateReason());
        dto.setTotalDays(entity.getTotalDays());
        dto.setManualDaysAllocation(entity.getManualDaysAllocation());
        dto.setCreatedDate(entity.getCreatedDate());

        dto.setReportingManagerStatus(entity.getReportingManagerStatus());
        dto.setHrStatus(entity.getHrStatus());
        dto.setReportingManagerUpdatedAt(entity.getReportingManagerUpdatedAt());
        dto.setHrUpdatedAt(entity.getHrUpdatedAt());
        dto.setReportingManagerComment(entity.getReportingManagerComment());
        dto.setHrComment(entity.getHrComment());


        if (entity.getMedicalCertificate() != null) {
            dto.setHasMedicalCertificate(true);
            dto.setMedicalCertificateName(entity.getMedicalCertificate().getFileName());
            dto.setFileUrl(entity.getMedicalCertificate().getFileUrl());
        } else {
            dto.setHasMedicalCertificate(false);
            dto.setMedicalCertificateName(null);
        }

        // near other dto setXXX lines
        if (entity.getSelectedDates() != null && !entity.getSelectedDates().isEmpty()) {
            List<String> list = entity.getSelectedDates().stream()
                    .map(LocalDate::toString) // "yyyy-MM-dd"
                    .collect(Collectors.toList());
            dto.setSelectedDates(list);
        } else {
            dto.setSelectedDates(Collections.emptyList());
        }



        // ✅ Newly added fields
        dto.setLeaveBalanceDeducted(entity.getLeaveBalanceDeducted());
        dto.setLeaveBalanceRecredited(entity.getLeaveBalanceRecredited());

        return dto;
    }






    @Override
    public List<LeaveRequestsDTO> getAllLeaveRequest() {
        // fetch leave types once and build map
        List<LeaveType> types = leaveTypeRepo.findAll();
        Map<String, LeaveType> typeMap = buildLeaveTypeMap(types);

        List<LeaveRequests> leaves = leaveRequestRepo.findAll();

        return leaves.stream()
                .map(entity -> {
                    LeaveRequestsDTO dto = convertToDto(entity);
                    enrichDtoWithDistribution(entity, dto, typeMap);
                    return dto;
                })
                .sorted((a, b) -> {
                    LocalDateTime aDate = a.getCreatedDate() != null ? a.getCreatedDate() : LocalDateTime.MIN;
                    LocalDateTime bDate = b.getCreatedDate() != null ? b.getCreatedDate() : LocalDateTime.MIN;
                    return bDate.compareTo(aDate); // newest first
                })
                .collect(Collectors.toList());
    }



    private boolean isMultipleOfHalf(double value) {
        return value * 10 % 5 == 0;
    }

    @Override
    public LeaveRequestsDTO getLeaveRequestById(Long id) {
        Optional<LeaveRequests> optional = leaveRequestRepo.findById(id);
        if (!optional.isPresent()) return null;

        LeaveRequests entity = optional.get();
        LeaveRequestsDTO dto = convertToDto(entity);

        List<LeaveType> types = leaveTypeRepo.findAll();
        Map<String, LeaveType> typeMap = buildLeaveTypeMap(types);
        enrichDtoWithDistribution(entity, dto, typeMap);

        return dto;
    }


    /*
    @Override
    LeaveRequestsDTO updateLeaveRequestStatus(Long requestId, LeaveRequestStatus newStatus, String role,  String comment) {
       System.out.println("[INFO] Updating leave request status. ID: " + requestId + ", New Status: " + newStatus);

        LeaveRequests request = leaveRequestRepo.findById(requestId)
                .orElseThrow(() -> {
                    System.out.println("[ERROR] Leave request not found: " + requestId);
                    throw new IllegalArgumentException("Leave request not found.");
                });

        LeaveRequestStatus oldStatus = request.getStatus();
        System.out.println("[INFO] Current status: " + oldStatus + ", Requested new status: " + newStatus);

        if (oldStatus == newStatus) {
            System.out.println("[INFO] Status is already " + newStatus + ". No changes needed.");
            return convertToDto(request);
        }

        Map<Long, Float> manualDaysAllocation = request.getManualDaysAllocation();
        if (manualDaysAllocation == null || manualDaysAllocation.isEmpty()) {
            System.out.println("[ERROR] No leave type allocation found for this request.");
            throw new IllegalStateException("No leave type allocation found for this request.");
        }

        for (Map.Entry<Long, Float> entry : manualDaysAllocation.entrySet()) {
            Long leaveTypeId = entry.getKey();
            Float allocatedDays = entry.getValue();

            Optional<EmployeeLeaveBalance> balanceOpt = employeeLeaveBalanceRepo
                    .findByEmployeeIdAndLeaveTypeId(request.getEmployeeId(), leaveTypeId);

            if (!balanceOpt.isPresent()) {
                System.out.println("[ERROR] Leave balance not found for employeeId: " + request.getEmployeeId() + " and leaveTypeId: " + leaveTypeId);
                throw new IllegalStateException("Leave balance not found.");
            }

            EmployeeLeaveBalance balance = balanceOpt.get();

            // Deduction or restoration logic based on status transitions
            if ((oldStatus == LeaveRequestStatus.REJECTED && newStatus == LeaveRequestStatus.APPROVED)
                    || (oldStatus == LeaveRequestStatus.REJECTED && newStatus == LeaveRequestStatus.PENDING)) {

                if (balance.getRemainingDays() < allocatedDays) {
                    System.out.println("[ERROR] Insufficient balance for leave type ID: " + leaveTypeId);
                    throw new IllegalStateException("Insufficient leave balance for approval/pending.");
                }
                balance.setRemainingDays(balance.getRemainingDays() - allocatedDays);
                System.out.println("[INFO] Deducted " + allocatedDays + " days from leaveTypeId: " + leaveTypeId);

            } else if ((oldStatus == LeaveRequestStatus.APPROVED && newStatus == LeaveRequestStatus.REJECTED)
                    || (oldStatus == LeaveRequestStatus.PENDING && newStatus == LeaveRequestStatus.REJECTED)) {

                balance.setRemainingDays(balance.getRemainingDays() + allocatedDays);
                System.out.println("[INFO] Restored " + allocatedDays + " days to leaveTypeId: " + leaveTypeId);
            }

            employeeLeaveBalanceRepo.save(balance);
            System.out.println("[INFO] Saved updated balance for leaveTypeId: " + leaveTypeId);
        }

        request.setStatus(newStatus);
        LeaveRequests updatedRequest = leaveRequestRepo.save(request);
        System.out.println("[SUCCESS] Leave request status updated successfully. New status: " + newStatus);

        return convertToDto(updatedRequest);


    }  */

    // @Override
    public LeaveRequestsDTO updateLeaveRequestStatus(Long requestId, LeaveRequestStatus newStatus, String role, String comment) {
        System.out.println("[INFO] Updating leave request status. ID: " + requestId + ", Role: " + role + ", New Status: " + newStatus);

        LeaveRequests request = leaveRequestRepo.findById(requestId)
                .orElseThrow(() -> {
                    System.out.println("[ERROR] Leave request not found: " + requestId);
                    throw new IllegalArgumentException("Leave request not found.");
                });

        Map<Long, Float> manualDaysAllocation = request.getManualDaysAllocation();
        if (manualDaysAllocation == null || manualDaysAllocation.isEmpty()) {
            System.out.println("[ERROR] No leave type allocation found for this request.");
            throw new IllegalStateException("No leave type allocation found for this request.");
        }

        // Update status and comment based on role
        if ("reporting manager".equalsIgnoreCase(role)) {
            request.setReportingManagerStatus(newStatus);
            request.setReportingManagerUpdatedAt(LocalDateTime.now());
            request.setReportingManagerComment(comment);
            System.out.println("[STATUS] Reporting Manager updated status to " + newStatus);

        } else if ("hr".equalsIgnoreCase(role)) {
            request.setHrStatus(newStatus);
            request.setHrUpdatedAt(LocalDateTime.now());
            request.setHrComment(comment);
            System.out.println("[STATUS] HR updated status to " + newStatus);
        } else {
            System.out.println("[ERROR] Invalid role: " + role);
            throw new IllegalArgumentException("Invalid role.");
        }

        // Apply or revert deduction only once, and only if both have approved/rejected
        boolean bothApproved = request.getReportingManagerStatus() == LeaveRequestStatus.APPROVED
                && request.getHrStatus() == LeaveRequestStatus.APPROVED;

        if (bothApproved && !Boolean.TRUE.equals(request.getLopGenerated())) {

            System.out.println("Before generate : " +request.getId() +" lopGenerated=" +request.getLopGenerated());

            System.out.println("[INFO] Generating monthly LOP for leave request ID: " + request.getId());

            generateMonthlyLop(request);

            request.setLopGenerated(true);

            System.out.println("After generate : " +request.getId() +" lopGenerated=" +request.getLopGenerated());
        }

        boolean bothRejected = request.getReportingManagerStatus() == LeaveRequestStatus.REJECTED
                && request.getHrStatus() == LeaveRequestStatus.REJECTED;

        if (bothRejected && !Boolean.TRUE.equals(request.getLeaveBalanceRecredited())) {

            System.out.println("[ACTION] Restoring leave balances.");

            // restore leave balances

            for (Map.Entry<Long, Float> entry : manualDaysAllocation.entrySet()) {

                Long leaveTypeId = entry.getKey();

                Float allocatedDays = entry.getValue();

                EmployeeLeaveBalance balance =
                        employeeLeaveBalanceRepo
                                .findByEmployeeIdAndLeaveTypeId(request.getEmployeeId(), leaveTypeId)
                                .orElseThrow(() -> new IllegalStateException("Balance not found"));

                balance.setRemainingDays(balance.getRemainingDays() + allocatedDays);
                employeeLeaveBalanceRepo.save(balance);

                System.out.println("[INFO] Recredited " + allocatedDays + " to leaveTypeId " + leaveTypeId);
            }

            // remove monthly lop
            if (Boolean.TRUE.equals(request.getLopGenerated())) {

                employeeMonthlyLopRepository.deleteByLeaveRequestId(request.getId());
                request.setLopGenerated(false);

                System.out.println("[INFO] Monthly LOP removed.");
            }

            request.setLeaveBalanceRecredited(true);
        }


//        if (bothApproved && !Boolean.TRUE.equals(request.getLeaveBalanceDeducted())) {
//            // Deduct balance only once
//            System.out.println("[ACTION] Both approvals received. Deducting leave balance.");
//            for (Map.Entry<Long, Float> entry : manualDaysAllocation.entrySet()) {
//                Long leaveTypeId = entry.getKey();
//                Float allocatedDays = entry.getValue();
//
//                EmployeeLeaveBalance balance = employeeLeaveBalanceRepo
//                        .findByEmployeeIdAndLeaveTypeId(request.getEmployeeId(), leaveTypeId)
//                        .orElseThrow(() -> new IllegalStateException("Leave balance not found for employee ID " + request.getEmployeeId()));
//
//                if (balance.getRemainingDays() < allocatedDays) {
//                    System.out.println("[ERROR] Insufficient balance for leaveTypeId: " + leaveTypeId);
//                    throw new IllegalStateException("Insufficient leave balance.");
//                }
//
//                balance.setRemainingDays(balance.getRemainingDays() - allocatedDays);
//                employeeLeaveBalanceRepo.save(balance);
//                System.out.println("[INFO] Deducted " + allocatedDays + " from leaveTypeId: " + leaveTypeId);
//            }
//            request.setLeaveBalanceDeducted(true);
//            request.setLeaveBalanceRecredited(false);

//        } else if (bothRejected && !Boolean.TRUE.equals(request.getLeaveBalanceRecredited())
//                && Boolean.TRUE.equals(request.getLeaveBalanceDeducted())) {
//
//            if(Boolean.TRUE.equals(request.getLopGenerated())) {
//
//                employeeMonthlyLopRepository.deleteByLeaveRequestId(request.getId());
//
//                request.setLopGenerated(false);
//            }
//            // Re-credit balance
//            System.out.println("[ACTION] Both rejections confirmed. Re-crediting leave balance.");
//            for (Map.Entry<Long, Float> entry : manualDaysAllocation.entrySet()) {
//                Long leaveTypeId = entry.getKey();
//                Float allocatedDays = entry.getValue();
//
//                EmployeeLeaveBalance balance = employeeLeaveBalanceRepo
//                        .findByEmployeeIdAndLeaveTypeId(request.getEmployeeId(), leaveTypeId)
//                        .orElseThrow(() -> new IllegalStateException("Leave balance not found for employee ID " + request.getEmployeeId()));
//
//                balance.setRemainingDays(balance.getRemainingDays() + allocatedDays);
//                employeeLeaveBalanceRepo.save(balance);
//                System.out.println("[INFO] Re-credited " + allocatedDays + " to leaveTypeId: " + leaveTypeId);
//            }
//            request.setLeaveBalanceRecredited(true);
//        }

        LeaveRequests updatedRequest = leaveRequestRepo.save(request);
        System.out.println("[SUCCESS] Leave request updated successfully.");
        return convertToDto(updatedRequest);
    }


    @Override
    public List<LeaveRequestsDTO> getLeaveRequestsByReportingManager(String reportingManagerEmployeeId) {
        System.out.println("[INFO] Fetching leave requests for manager ID: " + reportingManagerEmployeeId);

        // Step 1: Get employees reporting to this manager
        List<String> employeeIds = employeeService.getEmployeeIdsByReportingManager(reportingManagerEmployeeId);
        System.out.println("[INFO] Found " + employeeIds.size() + " employees reporting to manager: " + reportingManagerEmployeeId);
        System.out.println("[DEBUG] Employee IDs: " + employeeIds);

        if (employeeIds.isEmpty()) {
            System.out.println("[WARN] No employees found reporting to manager ID: " + reportingManagerEmployeeId);
            return Collections.emptyList();
        }

        // Step 2: Fetch leave requests for those employees
        List<LeaveRequests> leaveRequests = leaveRequestRepo.findByEmployeeIdIn(employeeIds);
        System.out.println("[INFO] Found " + leaveRequests.size() + " leave requests for manager's team.");

        // Step 3: Convert to DTOs
//        List<LeaveRequestsDTO> dtos = leaveRequests.stream()
//                .map(request -> {
//                    LeaveRequestsDTO dto = convertToDto(request);
//                    System.out.println("[DEBUG] Converted LeaveRequest ID: " + dto.getId() + " for employee: " + dto.getEmployeeName());
//                    return dto;
//                })
//                .collect(Collectors.toList());

        List<LeaveType> types = leaveTypeRepo.findAll();
        Map<String, LeaveType> typeMap = buildLeaveTypeMap(types);

        List<LeaveRequestsDTO> dtos = leaveRequests.stream()
                .map(request -> {
                    LeaveRequestsDTO dto = convertToDto(request);
                    enrichDtoWithDistribution(request, dto, typeMap);
                    System.out.println("[DEBUG] Converted LeaveRequest ID: " + dto.getId() + " for employee: " + dto.getEmployeeName());
                    return dto;
                })
                .collect(Collectors.toList());


        System.out.println("[SUCCESS] Completed leave request fetch for manager ID: " + reportingManagerEmployeeId);
        return dtos;
    }


    @Override
    public LeaveDocument getMedicalCertificate(Long leaveRequestId) {
        LeaveRequests request = leaveRequestRepo.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        return request.getMedicalCertificate();
    }



}