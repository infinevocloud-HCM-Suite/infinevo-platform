package com.itsdev.payroll.serviceimpl.employee;

import java.math.BigDecimal;

import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeBankDetailDTO;
import com.itsdev.payroll.dto.employee.EmployeeCTCDTO;
import com.itsdev.payroll.dto.employee.EmployeeFullProfileDTO;
import com.itsdev.payroll.dto.employee.EmployeePersonalDetailDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeeInvitation;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.EmployeeInvitationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.employee.EmployeeInvitationService;
import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.util.JWTUtil;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.service.employee.BasicDetailsService;
import com.itsdev.payroll.service.employee.CtcStructureService;
import com.itsdev.payroll.service.employee.EmployeeBankDetailService;
import com.itsdev.payroll.service.employee.EmployeePersonalDetailService;
import com.itsdev.payroll.service.employee.EmployyePortalService;

@Service
public class EmployyePortalServiceImpl implements EmployyePortalService{
	
	  private final BasicDetailsService basicDetailsService;
	    private final CtcStructureService ctcStructureService;
	    private final EmployeePersonalDetailService personalDetailService;
	    private final EmployeeBankDetailService bankDetailService;
	    private final BasicDetailsRepository basicDetailsRepository;
	    private final PayRunRepository payRunRepository;
	private final CtcStructureRepository ctcStructureRepository;
	private final EmployeeInvitationRepository employeeInvitationRepository;
	private final EmployeeInvitationService employeeInvitationService;
	private final BrevoEmailService emailService;
	    
		 private static final Logger log = LoggerFactory.getLogger(EmployyePortalServiceImpl.class);

	    public EmployyePortalServiceImpl(BasicDetailsService basicDetailsService,
	                                     CtcStructureService ctcStructureService,
	                                     EmployeePersonalDetailService personalDetailService,
	                                     EmployeeBankDetailService bankDetailService,
	                                     BasicDetailsRepository basicDetailsRepository,
	                                     PayRunRepository payRunRepository,
										 CtcStructureRepository ctcStructureRepository,
										 EmployeeInvitationRepository employeeInvitationRepository,
										 EmployeeInvitationService employeeInvitationService,
										 BrevoEmailService emailService) {
	        this.basicDetailsService = basicDetailsService;
	        this.ctcStructureService = ctcStructureService;
	        this.personalDetailService = personalDetailService;
	        this.bankDetailService = bankDetailService;
	        this.basicDetailsRepository = basicDetailsRepository; // ✅ assign repo
	        this.payRunRepository = payRunRepository;
			this.ctcStructureRepository = ctcStructureRepository;
			this.employeeInvitationRepository = employeeInvitationRepository;
			this.employeeInvitationService = employeeInvitationService;
			this.emailService = emailService;
	    }
	   

	    
	    
	    @Transactional(readOnly = true)
	    @Override
	    public EmployeeFullProfileDTO getEmployeeProfile(String organizationId, String employeeId) {
	        String method = "getEmployeeProfile";
	        log.info("[{}] 🔍 Searching employee with employee id: {}", method, employeeId);

	        EmployeeFullProfileDTO dto = new EmployeeFullProfileDTO();

	        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> new RuntimeException("Employee not found"));

	        log.info("[{}] ✅ Found employeeId={} for organizationId={}", method, employee.getId(), organizationId);

			employeeInvitationRepository.findByEmployeeIdAndOrganization_OrganizationId(employeeId, organizationId)
					.ifPresent(inv -> dto.setInvitationAccepted(inv.getIsInvitationAccepted()));

	        // ✅ Step 1: Basic Details
	        BasicDetailsDTO basic = basicDetailsService.getBasicDetails(organizationId, employeeId);
	        dto.setBasicDetails(basic);
	        dto.markStepComplete("basicDetails", basic != null);

	        // ✅ Step 2: CTC
			// ✅ Step 2: CTC (fetch ACTIVE CTC)
			EmployeeCTCDTO ctc = null;

			CtcStructure activeCtc = ctcStructureRepository
					.findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
							organizationId,
							employee.getId()
					)
					.orElse(null);

			if (activeCtc != null) {
				ctc = ctcStructureService.get(organizationId, activeCtc.getId());
				dto.setTotalEarnings(calculateTotalEarnings(ctc));
			}

			dto.setCtc(ctc);
			dto.markStepComplete("ctc", ctc != null);


			// ✅ Step 2.1 : Latest revised CTC (for UI – process later / status view)

			CtcStructure latestRevision = ctcStructureRepository
					.findFirstByOrganization_OrganizationIdAndEmployee_IdAndRevisionStatusIsNotNullAndDeletedFalseOrderByCreatedAtDesc(
							organizationId,
							employee.getId()
					)
					.orElse(null);

			if (latestRevision != null) {

				EmployeeCTCDTO revisionDto =
						ctcStructureService.get(organizationId, latestRevision.getId());

				dto.setLatestRevisionCtc(revisionDto);
			}


			// ✅ Step 3: Personal Detail
	        EmployeePersonalDetailDTO personal =  null;
	        if (employee.getPersonalDetail() != null) {
	        	personal = personalDetailService.getByEmployeeId(organizationId, employeeId);
	        }
	        dto.setPersonalDetail(personal);
	        dto.markStepComplete("personalDetail", personal != null);

	        // ✅ Step 4: Bank Detail
	        EmployeeBankDetailDTO bank =  null;
	        if (employee.getBankDetail() != null) {
	        	 bank = bankDetailService.getByEmployeeId(organizationId, employeeId);
	        }
	        dto.setBankDetail(bank);
	        dto.markStepComplete("bankDetail", bank != null);

	        log.info("[{}] 🎯 Full employee profile prepared for employeeId={}", method, employee.getId());
	        return dto;
	    }
	    
	    private BigDecimal calculateTotalEarnings(EmployeeCTCDTO ctcDTO) {
	        String method = "calculateTotalEarnings";

	        log.info("[{}]  Starting total earnings calculation", method);

	        BigDecimal total = BigDecimal.ZERO;

	        // 1. Fixed Earnings
	        if (ctcDTO.getEarnings() != null) {
	            log.info("[{}]  Found {} earning components", 
	                     method, ctcDTO.getEarnings().size());

	            BigDecimal fixedEarnings = ctcDTO.getEarnings().stream()
	                .peek(e -> log.info("[{}] ➡️ Earning: code={}, name={}, enabled={}, amount={}", 
	                                     method,
	                                     e.getEarningCode(), 
	                                     e.getName(), 
	                                     e.getEnabled(), 
	                                     e.getAmount()))
	                .filter(e -> Boolean.TRUE.equals(e.getEnabled()))
	                .map(e -> BigDecimal.valueOf(
	                        e.getAmount() != null ? e.getAmount() : 0
	                ))
	                .reduce(BigDecimal.ZERO, BigDecimal::add);

	            log.info("[{}]  Fixed earnings total: {}", method, fixedEarnings);

	            total = total.add(fixedEarnings);
	        } else {
	            log.warn("[{}]  No earnings found in CTC structure", method);
	        }

	        log.info("[{}]  Total earnings computed: {}", method, total);

	        return total;
	    }


	    
	    @Transactional
	    public BasicDetailsDTO activateEmployee(String organizationId, String employeeId) {
	        String method = "activateEmployee";
	        log.info("[{}] 📥 Incoming request | Activate employee | orgId={}, employeeId={}",
	                 method, organizationId, employeeId);

	        BasicDetails employee = basicDetailsRepository.
	        		findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> {
	                    log.error("[{}] ❌ Employee not found | orgId={}, employeeId={}",
	                              method, organizationId, employeeId);
	                    return new RuntimeException("Employee not found for orgId=" + organizationId + " and employeeId=" + employeeId);
	                });

	        employee.setEmployeeStatus("ACTIVE");
	        BasicDetails updated = basicDetailsRepository.save(employee);

	        log.info("[{}] ✅ Employee activated successfully | orgId={}, employeeId={}, status={}",
	                 method, organizationId, employeeId, updated.getEmployeeStatus());
	        
	       return basicDetailsService.mapToDto(updated);
	    }

	    @Transactional
	    public BasicDetailsDTO deactivateEmployee(String organizationId, String employeeId) {
	        String method = "deactivateEmployee";
	        log.info("[{}] 📥 Incoming request | Deactivate employee | orgId={}, employeeId={}",
	                 method, organizationId, employeeId);

	        BasicDetails employee = basicDetailsRepository
	                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> {
	                    log.error("[{}] ❌ Employee not found | orgId={}, employeeId={}",
	                              method, organizationId, employeeId);
	                    return new RuntimeException("Employee not found for orgId=" + organizationId + " and employeeId=" + employeeId);
	                });

	        employee.setEmployeeStatus("INACTIVE");
	        BasicDetails updated = basicDetailsRepository.save(employee);

	        log.info("[{}] ✅ Employee deactivated successfully | orgId={}, employeeId={}, status={}",
	                 method, organizationId, employeeId, updated.getEmployeeStatus());

	        return basicDetailsService.mapToDto(updated);
	    }
	    
	    @Transactional
	    public BasicDetailsDTO softDeleteEmployee(String organizationId, String employeeId) {
	        String method = "softDeleteEmployee";
	        log.info("[{}] 📥 Incoming request | Soft delete employee | orgId={}, employeeId={}",
	                method, organizationId, employeeId);
	        
	        long activePayrunCount = payRunRepository.countActiveRegularPayRuns(organizationId, employeeId);

	        if (activePayrunCount > 0) {
	            log.warn("[{}] ❌ Cannot delete employee. Employee is part of an active regular payrun | orgId={}, employeeId={}",
	                    method, organizationId, employeeId);
	            throw new RuntimeException("Employee cannot be deleted because they are part of an active regular payrun");
	        }

	        BasicDetails employee = basicDetailsRepository
	                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> {
	                    log.error("[{}] ❌ Employee not found or already deleted | orgId={}, employeeId={}",
	                            method, organizationId, employeeId);
	                    return new RuntimeException("Employee not found or already deleted");
	                });

	        employee.setIsDeleted(true);
	        employee.setEmployeeStatus("INACTIVE");

	        BasicDetails updated = basicDetailsRepository.save(employee);

	        log.info("[{}] ✅ Employee soft deleted successfully | orgId={}, employeeId={}",
	                method, organizationId, employeeId);

	        return basicDetailsService.mapToDto(updated);
	    }

	    @Transactional
	    @Override
	    public void enablePortal(String organizationId, String employeeId) {
	        String method = "enablePortal";
	        log.info("[{}] 📥 Incoming request | Enable portal | orgId={}, employeeId={}", method, organizationId, employeeId);
	        
	        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> new RuntimeException("Employee not found"));
	                
	        employee.setPortalEnabled(true);
	        basicDetailsRepository.save(employee);

	        EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
            invitationDto.setEmail(employee.getWorkMail());
            invitationDto.setEmployeeId(employee.getEmployeeId());
            invitationDto.setIsPortalEnabled(true);
            invitationDto.setIsInvitationAccepted(false);
            
            EmployeeInvitationDTO savedInvitation = employeeInvitationRepository.findByEmployeeIdAndOrganization_OrganizationId(employeeId, organizationId)
                    .map(existing -> employeeInvitationService.updateInvitation(organizationId, existing.getInvitationId(), invitationDto))
                    .orElseGet(() -> employeeInvitationService.createInvitation(organizationId, invitationDto));

            try {
                String inviterName = JWTUtil.getCurrentUserName();
                boolean emailSent = emailService.sendEmployeeInvitationEmail(
                        employee.getWorkMail(),
                        employee.getFirstName() + " " + employee.getLastName(),
                        employee.getOrganization().getOrganizationName(),
                        inviterName,
                        "Employee",
                        emailService.calculateExpiryDate(),
                        savedInvitation.getAcceptanceToken(),
                        organizationId);

                if (emailSent) {
                    log.info("[{}] ✅ Employee invitation email sent successfully to: {}", method, employee.getWorkMail());
                } else {
                    log.warn("[{}] ⚠️ Failed to send invitation email to: {}", method, employee.getWorkMail());
                }
            } catch (Exception e) {
                log.error("[{}] ❌ Error sending invitation email: {}", method, e.getMessage());
            }
	    }
	    
	    @Transactional
	    @Override
	    public void resendInvitation(String organizationId, String employeeId) {
	        String method = "resendInvitation";
	        log.info("[{}] 📥 Incoming request | Resend invitation | orgId={}, employeeId={}", method, organizationId, employeeId);
	        
	        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
	                .orElseThrow(() -> new RuntimeException("Employee not found"));
	                
	        if (!Boolean.TRUE.equals(employee.getPortalEnabled())) {
	            throw new RuntimeException("Portal access is not enabled for this employee");
	        }

            EmployeeInvitation existing = employeeInvitationRepository.findByEmployeeIdAndOrganization_OrganizationId(employeeId, organizationId)
                    .orElseThrow(() -> new RuntimeException("Invitation record not found"));
                    
            // Removed check so that HR can re-invite to reset password
            
            EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
            invitationDto.setEmail(employee.getWorkMail());
            invitationDto.setEmployeeId(employee.getEmployeeId());
            invitationDto.setIsPortalEnabled(true);
            invitationDto.setIsInvitationAccepted(false);
            
            EmployeeInvitationDTO updatedInvitation = employeeInvitationService.updateInvitation(organizationId, existing.getInvitationId(), invitationDto);

            try {
                String inviterName = JWTUtil.getCurrentUserName();
                boolean emailSent = emailService.sendEmployeeInvitationEmail(
                        employee.getWorkMail(),
                        employee.getFirstName() + " " + employee.getLastName(),
                        employee.getOrganization().getOrganizationName(),
                        inviterName,
                        "Employee",
                        emailService.calculateExpiryDate(),
                        updatedInvitation.getAcceptanceToken(),
                        organizationId);

                if (emailSent) {
                    log.info("[{}] ✅ Employee invitation email resent successfully to: {}", method, employee.getWorkMail());
                } else {
                    log.warn("[{}] ⚠️ Failed to resend invitation email to: {}", method, employee.getWorkMail());
                }
            } catch (Exception e) {
                log.error("[{}] ❌ Error resending invitation email: {}", method, e.getMessage());
            }
	    }


}
