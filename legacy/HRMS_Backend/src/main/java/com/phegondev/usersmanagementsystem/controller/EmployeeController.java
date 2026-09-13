package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.CloudinaryUploadResponseDto;
import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.entity.Employee;
import com.phegondev.usersmanagementsystem.entity.EmployeeDocument;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.enumuration.DocumentType;
import com.phegondev.usersmanagementsystem.repository.EmployeeDocumentRepository;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.service.CloudinaryServiceImpl;
import com.phegondev.usersmanagementsystem.service.EmployeeService;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepository;
    
    @Autowired
    private  CloudinaryServiceImpl cloudinaryService;



    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addEmployee(
            @Valid @RequestPart("employee") Employee employee,
            @RequestParam Map<String, MultipartFile> filesMap,
            BindingResult result
    ) {
        System.out.println("=== [INFO] Received request to add new employee ===");

        if (result.hasErrors()) {
            System.out.println("=== [VALIDATION ERROR] Employee payload validation failed ===");
            result.getFieldErrors().forEach(err ->
                    System.out.println("Field Error -> " + err.getField() + ": " + err.getDefaultMessage())
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("errors", result.getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.toList()));
            return ResponseEntity.badRequest().body(response);
        }

        try {
            System.out.println("=== [INFO] Files received: " + filesMap.size() + " ===");

            // Generate employee ID first (assuming this is done in the service)
        String empId = employeeService.generateNextEmpId(employee.getPersonal().getEmploymentStatus());
        employee.getPersonal().setEmpId(empId);
        System.out.println("=== [INFO] Generated employee ID: " + empId + " ===");

            List<EmployeeDocument> documentList = new ArrayList<>();

//            for (Map.Entry<String, MultipartFile> entry : filesMap.entrySet()) {
//                String docTypeKey = entry.getKey();
//                MultipartFile file = entry.getValue();
//
//                System.out.println("-> Processing file for DocumentType: " + docTypeKey);
//                System.out.println("   File Name: " + file.getOriginalFilename());
//                System.out.println("   File Type: " + file.getContentType());
//                System.out.println("   File Size: " + file.getSize() + " bytes");
//
//                if ("employee".equalsIgnoreCase(docTypeKey)) {
//                    System.out.println("[INFO] Skipping key 'employee' from document processing.");
//                    continue;
//                }
//
//                if (!file.isEmpty()) {
//                    EmployeeDocument doc = new EmployeeDocument();
//                    doc.setDocumentType(DocumentType.valueOf(docTypeKey));
//                    doc.setFileName(file.getOriginalFilename());
//                    doc.setFileType(file.getContentType());
//                    doc.setDocumentContent(file.getBytes());
//                    doc.setEmployee(employee); // Link back
//
//                    documentList.add(doc);
//                    System.out.println("   -> Document added to employee document list");
//                } else {
//                    System.out.println("   [WARNING] Skipping empty file for type: " + docTypeKey);
//                }
//            }



            // In addEmployee method, update the document processing loop
            for (Map.Entry<String, MultipartFile> entry : filesMap.entrySet()) {
                String docTypeKey = entry.getKey();
                MultipartFile file = entry.getValue();

                System.out.println("-> Processing file with key: " + docTypeKey);

                if ("employee".equalsIgnoreCase(docTypeKey)) {
                    System.out.println("[INFO] Skipping key 'employee' from document processing.");
                    continue;
                }

                if (!file.isEmpty()) {
                    // Parse the document key to extract type and company index
                    String[] parts = docTypeKey.split("_COMPANY_");
                    String docTypeStr = parts[0];
                    int companyIndex = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

                    try {
                        DocumentType docType = DocumentType.valueOf(docTypeStr);
                        EmployeeDocument doc = new EmployeeDocument();
                        doc.setDocumentType(docType);
                        doc.setFileName(file.getOriginalFilename());
                        doc.setFileType(file.getContentType());
                    //    doc.setDocumentContent(file.getBytes());
                        
                        CloudinaryUploadResponseDto responseDto = cloudinaryService.uploadFile(file,empId);
                        doc.setFileUrl(responseDto.getSecure_url());
                        doc.setPublicId(responseDto.getPublic_id());
                        doc.setEmployee(employee);
                        doc.setCompanyIndex(companyIndex); // New field to track company

                        documentList.add(doc);
                        System.out.println("   -> Document added for company index: " + companyIndex);
                    } catch (IllegalArgumentException e) {
                        System.out.println("   [WARNING] Invalid document type: " + docTypeStr);
                    }
                }
            }

            // Attach documents
            employee.setDocuments(documentList);
            System.out.println("=== [INFO] All documents processed and attached to employee ===");

            // Save employee
            Employee savedEmployee = employeeService.saveEmployee(employee);
            System.out.println("=== [SUCCESS] Employee saved with ID: " + savedEmployee.getId() + " ===");

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", savedEmployee);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            System.out.println("=== [ERROR] Invalid document type provided: " + e.getMessage() + " ===");
            return ResponseEntity.badRequest().body("Invalid document type in file keys. Must match enum values.");
        } catch (Exception e) {
            System.out.println("=== [EXCEPTION] Error while saving employee: " + e.getMessage() + " ===");
            e.printStackTrace(); // full trace in console
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to save employee: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    @GetMapping("/search")
    public ResponseEntity<?> searchEmployees(@RequestParam String query) {
        try {
            List<Map<String, String>> employees = employeeService.searchEmployees(query);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", employees));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Failed to search employees: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllEmployees() {
        try {
            List<Employee> employees = employeeService.getAllEmployees();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", employees);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse("Failed to fetch employees: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEmployeeById(@PathVariable Long id) {
        try {
            Optional<Employee> employee = employeeService.getEmployeeById(id);
            if (employee.isPresent()) {
                // Create a response object that includes the employee and their documents
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", employee.get());

                // Add documents to the response if they exist
                if (employee.get().getDocuments() != null && !employee.get().getDocuments().isEmpty()) {
                    List<Map<String, Object>> documentsData = employee.get().getDocuments().stream()
                            .map(doc -> {
                                Map<String, Object> docMap = new HashMap<>();
                                docMap.put("id", doc.getId());
                                docMap.put("documentType", doc.getDocumentType());
                                docMap.put("fileName", doc.getFileName());
                                docMap.put("fileType", doc.getFileType());
                                docMap.put("fileUrl", doc.getFileUrl());
                                return docMap;
                            })
                            .collect(Collectors.toList());
                    response.put("documents", documentsData);
                }

                return ResponseEntity.ok(response);
            } else {
                return errorResponse("Employee not found with id: " + id, HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return errorResponse("Failed to fetch employee: " + e.getMessage());
        }
    }

    /*
    @GetMapping("/{employeeId}/documents/{documentId}")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable Long employeeId,
            @PathVariable Long documentId
    ) {
        try {
            // Verify the document belongs to the employee
            EmployeeDocument document = employeeDocumentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            if (!document.getEmployee().getId().equals(employeeId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(document.getFileType()));
            headers.setContentDisposition(
                    ContentDisposition.inline()
                            .filename(document.getFileName())
                            .build()
            );

            return new ResponseEntity<>(document.getDocumentContent(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    } */

    @GetMapping("/ids")
    public ResponseEntity<?> getAllEmployeeIds() {
        try {
            List<String> employeeIds = employeeService.getAllEmployeeIds();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", employeeIds);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse("Failed to fetch employee IDs: " + e.getMessage());
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestPart("employee") Employee employeeDetails,
            @RequestParam Map<String, MultipartFile> filesMap,
            BindingResult result) {

        if (result.hasErrors()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("errors", result.getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.toList()));
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Optional<Employee> existingEmployeeOpt = employeeService.getEmployeeById(id);
            if (existingEmployeeOpt.isPresent()) {
                Employee existingEmployee = existingEmployeeOpt.get();
                String empId = existingEmployee.getEmpId();

                // Update nested entities
                if (employeeDetails.getPersonal() != null) {
                    if (existingEmployee.getPersonal() == null) {
                        existingEmployee.setPersonal(employeeDetails.getPersonal());
                    } else {
                        existingEmployee.getPersonal()
                                .setEmploymentStatus(employeeDetails.getPersonal().getEmploymentStatus());
                        existingEmployee.getPersonal().setEmpId(employeeDetails.getPersonal().getEmpId());
                        existingEmployee.getPersonal().setFirstName(employeeDetails.getPersonal().getFirstName());
                        existingEmployee.getPersonal().setMiddleName(employeeDetails.getPersonal().getMiddleName());
                        existingEmployee.getPersonal().setLastName(employeeDetails.getPersonal().getLastName());
                        existingEmployee.getPersonal().setDateOfBirth(employeeDetails.getPersonal().getDateOfBirth());
                        existingEmployee.getPersonal().setGender(employeeDetails.getPersonal().getGender());
                        existingEmployee.getPersonal()
                                .setMaritalStatus(employeeDetails.getPersonal().getMaritalStatus());
                        existingEmployee.getPersonal().setNationality(employeeDetails.getPersonal().getNationality());
                        existingEmployee.getPersonal().setEthnicity(employeeDetails.getPersonal().getEthnicity());
                    }
                    existingEmployee.getPersonal().setEmployee(existingEmployee);
                }

                if (employeeDetails.getIdentification() != null) {
                    if (existingEmployee.getIdentification() == null) {
                        existingEmployee.setIdentification(employeeDetails.getIdentification());
                    } else {
                        existingEmployee.getIdentification()
                                .setImmigrationStatus(employeeDetails.getIdentification().getImmigrationStatus());
                        existingEmployee.getIdentification()
                                .setAadharCardNumber(employeeDetails.getIdentification().getAadharCardNumber());
                        existingEmployee.getIdentification()
                                .setPanCardNumber(employeeDetails.getIdentification().getPanCardNumber());
                        existingEmployee.getIdentification()
                                .setAddressProof(employeeDetails.getIdentification().getAddressProof());
                        existingEmployee.getIdentification()
                                .setAddressDocumentName(employeeDetails.getIdentification().getAddressDocumentName());
                        existingEmployee.getIdentification()
                                .setAddressDocumentNumber(
                                        employeeDetails.getIdentification().getAddressDocumentNumber());
                        existingEmployee.getIdentification()
                                .setPersonalTaxId(employeeDetails.getIdentification().getPersonalTaxId());
                        existingEmployee.getIdentification()
                                .setSocialInsurance(employeeDetails.getIdentification().getSocialInsurance());
                        existingEmployee.getIdentification()
                                .setIdProof(employeeDetails.getIdentification().getIdProof());
                        existingEmployee.getIdentification()
                                .setDocumentName(employeeDetails.getIdentification().getDocumentName());
                        existingEmployee.getIdentification()
                                .setDocumentNumber(employeeDetails.getIdentification().getDocumentNumber());
                    }
                    existingEmployee.getIdentification().setEmployee(existingEmployee);
                }

                if (employeeDetails.getWork() != null) {
                    if (existingEmployee.getWork() == null) {
                        existingEmployee.setWork(employeeDetails.getWork());
                    } else {
                        existingEmployee.getWork().setDepartment(employeeDetails.getWork().getDepartment());
                        existingEmployee.getWork().setJobTitle(employeeDetails.getWork().getJobTitle());
                        existingEmployee.getWork().setPayGrade(employeeDetails.getWork().getPayGrade());
                        existingEmployee.getWork().setDoj(employeeDetails.getWork().getDoj());
                        existingEmployee.getWork().setTerminationDate(employeeDetails.getWork().getTerminationDate());
                        existingEmployee.getWork().setWorkstationId(employeeDetails.getWork().getWorkstationId());
                        existingEmployee.getWork().setTimeZone(employeeDetails.getWork().getTimeZone());
                        existingEmployee.getWork().setShiftStartTime(employeeDetails.getWork().getShiftStartTime());
                        existingEmployee.getWork().setShiftEndTime(employeeDetails.getWork().getShiftEndTime());
                    }
                    existingEmployee.getWork().setEmployee(existingEmployee);
                }

                if (employeeDetails.getContact() != null) {
                    if (existingEmployee.getContact() == null) {
                        existingEmployee.setContact(employeeDetails.getContact());
                    } else {
                        existingEmployee.getContact()
                                .setResidentialAddress(employeeDetails.getContact().getResidentialAddress());
                        existingEmployee.getContact()
                                .setPermanentAddress(employeeDetails.getContact().getPermanentAddress());
                        existingEmployee.getContact().setCity(employeeDetails.getContact().getCity());
                        existingEmployee.getContact().setState(employeeDetails.getContact().getState());
                        existingEmployee.getContact().setCountry(employeeDetails.getContact().getCountry());
                        existingEmployee.getContact().setPostalCode(employeeDetails.getContact().getPostalCode());
                        existingEmployee.getContact().setWorkEmail(employeeDetails.getContact().getWorkEmail());
                        existingEmployee.getContact().setPersonalEmail(employeeDetails.getContact().getPersonalEmail());
                        existingEmployee.getContact().setMobileNumber(employeeDetails.getContact().getMobileNumber());
                        existingEmployee.getContact().setPrimaryEmergencyContactName(
                                employeeDetails.getContact().getPrimaryEmergencyContactName());
                        existingEmployee.getContact().setPrimaryEmergencyContactNumber(
                                employeeDetails.getContact().getPrimaryEmergencyContactNumber());
                        existingEmployee.getContact().setRelationshipToPrimaryEmergencyContact(
                                employeeDetails.getContact().getRelationshipToPrimaryEmergencyContact());
                        existingEmployee.getContact().setSecondaryEmergencyContactName(
                                employeeDetails.getContact().getSecondaryEmergencyContactName());
                        existingEmployee.getContact().setSecondaryEmergencyContactNumber(
                                employeeDetails.getContact().getSecondaryEmergencyContactNumber());
                        existingEmployee.getContact().setRelationshipToSecondaryEmergencyContact(
                                employeeDetails.getContact().getRelationshipToSecondaryEmergencyContact());
                        existingEmployee.getContact()
                                .setFamilyDoctorName(employeeDetails.getContact().getFamilyDoctorName());
                        existingEmployee.getContact().setFamilyDoctorContactNumber(
                                employeeDetails.getContact().getFamilyDoctorContactNumber());
                    }
                    existingEmployee.getContact().setEmployee(existingEmployee);
                }

                if (employeeDetails.getReport() != null) {
                    if (existingEmployee.getReport() == null) {
                        existingEmployee.setReport(employeeDetails.getReport());
                    } else {
                        existingEmployee.getReport().setReportingManagerId(
                                employeeDetails.getReport().getReportingManagerId());
                        existingEmployee.getReport()
                                .setReportingManagerName(employeeDetails.getReport().getReportingManagerName());
                        existingEmployee.getReport()
                                .setIndirectManager(employeeDetails.getReport().getIndirectManager());
                        existingEmployee.getReport()
                                .setFirstLevelApprover(employeeDetails.getReport().getFirstLevelApprover());
                        existingEmployee.getReport()
                                .setSecondLevelApprover(employeeDetails.getReport().getSecondLevelApprover());
                        existingEmployee.getReport()
                                .setThirdLevelApprover(employeeDetails.getReport().getThirdLevelApprover());
                        existingEmployee.getReport().setNote(employeeDetails.getReport().getNote());
                    }
                    existingEmployee.getReport().setEmployee(existingEmployee);
                }

                // Handle document updates
                if (!filesMap.isEmpty()) {
                    List<EmployeeDocument> documentList = new ArrayList<>();

                    for (Map.Entry<String, MultipartFile> entry : filesMap.entrySet()) {
                        String docTypeKey = entry.getKey();
                        MultipartFile file = entry.getValue();

                        if ("employee".equalsIgnoreCase(docTypeKey)) {
                            continue;
                        }

                        if (!file.isEmpty()) {
                            // Parse the document key to extract type and company index
                            String[] parts = docTypeKey.split("_COMPANY_");
                            String docTypeStr = parts[0];
                            int companyIndex = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

                            try {
                                DocumentType docType = DocumentType.valueOf(docTypeStr);
                                EmployeeDocument doc = new EmployeeDocument();
                                doc.setDocumentType(docType);
                                doc.setFileName(file.getOriginalFilename());
                                doc.setFileType(file.getContentType());
                          //      doc.setDocumentContent(file.getBytes());
                                
                                CloudinaryUploadResponseDto responseDto = cloudinaryService.uploadFile(file,empId);
                                doc.setFileUrl(responseDto.getSecure_url());
                                doc.setPublicId(responseDto.getPublic_id());
                                
                                doc.setEmployee(existingEmployee);
                                doc.setCompanyIndex(companyIndex); // Set the company index

                                documentList.add(doc);
                            } catch (IllegalArgumentException e) {
                                System.out.println("Invalid document type: " + docTypeStr);
                                continue;
                            }
                        }
                    }

                    // Attach new documents
                    if (existingEmployee.getDocuments() == null) {
                        existingEmployee.setDocuments(documentList);
                    } else {
                        existingEmployee.getDocuments().addAll(documentList);
                    }
                }

                Employee updatedEmployee = employeeService.saveEmployee(existingEmployee);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", updatedEmployee);
                return ResponseEntity.ok(response);
            } else {
                return errorResponse("Employee not found with id: " + id, HttpStatus.NOT_FOUND);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid document type in file keys. Must match enum values.");
        } catch (Exception e) {
            return errorResponse("Failed to update employee: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
    try {
        Optional<Employee> employee = employeeService.getEmployeeById(id);
        if (employee.isPresent()) {
            // First delete all documents associated with the employee from Cloudinary and database
            List<EmployeeDocument> documents = employee.get().getDocuments();
            if (documents != null && !documents.isEmpty()) {
                for (EmployeeDocument doc : documents) {
                    try {
                        // Delete from Cloudinary if public_id exists
                        if (doc.getPublicId() != null && !doc.getPublicId().isEmpty()) {
                            cloudinaryService.deleteFile(doc.getPublicId());
                            System.out.println("Deleted document from Cloudinary: " + doc.getPublicId());
                        }
                    } catch (Exception e) {
                        System.out.println("Error deleting document from Cloudinary: " + e.getMessage());
                        // Continue with deletion even if Cloudinary deletion fails
                    }
                }
                // Delete all documents from database
                employeeDocumentRepository.deleteAll(documents);
                System.out.println("Deleted " + documents.size() + " documents from database");
            }

            // Then delete the employee
            employeeService.deleteEmployee(id);
            System.out.println("Deleted employee with ID: " + id);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Employee and all associated documents deleted successfully");
            return ResponseEntity.ok(response);
        } else {
            return errorResponse("Employee not found with id: " + id, HttpStatus.NOT_FOUND);
        }
    } catch (Exception e) {
        System.out.println("=== [EXCEPTION] Error while deleting employee and documents: " + e.getMessage() + " ===");
        e.printStackTrace();
        return errorResponse("Failed to delete employee and documents: " + e.getMessage());
    }
}

    private ResponseEntity<?> errorResponse(String message) {
        return errorResponse(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<?> errorResponse(String message, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", message);
        return ResponseEntity.status(status).body(errorResponse);
    }

    @DeleteMapping("/{employeeId}/documents/{documentId}")
public ResponseEntity<?> deleteDocument(
        @PathVariable Long employeeId,
        @PathVariable Long documentId) {
    try {
        // Verify the document exists and belongs to the employee
        Optional<EmployeeDocument> documentOpt = employeeDocumentRepository.findById(documentId);

        if (documentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", "error",
                            "message", "Document not found"
                    ));
        }

        EmployeeDocument document = documentOpt.get();

        // Verify the document belongs to the specified employee
        if (!document.getEmployee().getId().equals(employeeId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "status", "error",
                            "message", "Document does not belong to this employee"
                    ));
        }

        // First delete from Cloudinary if public_id exists
        if (document.getPublicId() != null && !document.getPublicId().isEmpty()) {
            try {
                cloudinaryService.deleteFile(document.getPublicId());
            } catch (Exception e) {
                System.out.println("Failed to delete from Cloudinary: " + e.getMessage());
                // Continue with database deletion even if Cloudinary deletion fails
            }
        }

        // Then delete the document from database
        employeeDocumentRepository.delete(document);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Document deleted successfully"
        ));
    } catch (Exception e) {
        System.out.println("=== [EXCEPTION] Error while deleting document: " + e.getMessage() + " ===");
        e.printStackTrace();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", "error",
                        "message", "Failed to delete document: " + e.getMessage()
                ));
    }
}

    @GetMapping("/by-emp-id/{empId}")
    public ResponseEntity<?> getEmployeeByEmpId(@PathVariable String empId) {
        try {
            Optional<Employee> employee = employeeService.getEmployeeByEmpId(empId);
            if (employee.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", employee.get());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Employee not found with empId: " + empId));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to fetch employee: " + e.getMessage()));
        }
    }

    @GetMapping("/next-emp-id")
    public ResponseEntity<?> getNextEmpId(@RequestParam String employmentStatus) {
        try {
            String nextEmpId = employeeService.generateNextEmpId(employmentStatus);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "data", nextEmpId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Failed to generate employee ID: " + e.getMessage()));
        }
    }

    @PutMapping("/{userId}/access")
    public ResponseEntity<String> updateUserAccess(
            @PathVariable Integer userId,
            @RequestParam boolean isNonBlocked) {

        System.out.println(
                "Received request to update access for user ID: " + userId + " to isNonBlocked = " + isNonBlocked);

        String result = employeeService.updateUserAccess(userId, isNonBlocked);

        System.out.println("Response to client: " + result);
        return ResponseEntity.ok(result);
    }

 @GetMapping("/reporting-manager")
public List<UserDTO> getEmployeesByReportingManagerId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    OurUsers user = (OurUsers) authentication.getPrincipal();

    String empId = user.getEmpId();
    System.out.println("🔐 Authenticated user empId (reporting manager): " + empId);

    List<UserDTO> employeeList = employeeService.getEmployeesByReportingManagerId(empId);
    System.out.println("📋 Total employees found for manager " + empId + ": " + employeeList.size());

    return employeeList;
}
 
@GetMapping("/excludes-user-role")
 public ResponseEntity<List<UserDTO>> getUsersExcludingRole() {
     System.out.println("#####[Controller]##### Starting to fetch users excluding role 'USER'");

     List<UserDTO> dtos = employeeService.getUsersExcludingRole("user");

     System.out.println("#####[Controller]##### Total users fetched (excluding 'USER' role): " + dtos.size());
     return ResponseEntity.ok(dtos);
 }



}