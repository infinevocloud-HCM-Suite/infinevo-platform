package com.itsdev.payroll.controller.employeeitdeclaration.taxCalculator;

import com.itsdev.payroll.controller.employee.EmployyePortalContoller;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.NewTaxCalculationService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.OldTaxCalculationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tax/calculate")
public class TaxCalculationController {

	private final OldTaxCalculationService oldTaxCalculationService;
	private final NewTaxCalculationService newTaxCalculationService;
	
	 private static final Logger log = LoggerFactory.getLogger(TaxCalculationController.class);

	public TaxCalculationController(OldTaxCalculationService oldTaxCalculationService,
			NewTaxCalculationService newTaxCalculationService) {
		this.oldTaxCalculationService = oldTaxCalculationService;
		this.newTaxCalculationService = newTaxCalculationService;
	}

	/*
	 * ===================================================== GET OLD TAX CALCULATION
	 * =====================================================
	 */

	@GetMapping("/old/{employeeId}/{financialYear}")
	public ResponseEntity<OldTaxCalculationResult> calculateOldTax(
			@RequestHeader("organizationId") String organizationId, // ✅ REQUIRED
			@PathVariable String employeeId, @PathVariable Integer financialYear) {

		OldTaxCalculationResult result = oldTaxCalculationService.calculateOldTax(organizationId, // ✅ PASS ORG
				employeeId, financialYear);

		return ResponseEntity.ok(result);
	}

	/*
	 * ===================================================== GET NEW TAX CALCULATION
	 * =====================================================
	 */
	@GetMapping("/new/{employeeId}/{financialYear}")
	public ResponseEntity<NewTaxCalculationResult> calculateNewTax(
			@RequestHeader("organizationId") String organizationId, @PathVariable String employeeId,
			@PathVariable Integer financialYear) {
		return ResponseEntity.ok(newTaxCalculationService.calculateNewTax(organizationId, employeeId, financialYear));
	}

	@PostMapping("/old/save/{employeeId}/{financialYear}")
	public ResponseEntity<OldTaxCalculationResult> saveOldTaxCalculation(
			@RequestHeader("organizationId") String organizationId,
			@PathVariable String employeeId,
			@PathVariable Integer financialYear
	) {

		final String method = "saveOldTaxCalculation";

		log.info(
				"[{}] 📥 Incoming request to SAVE OLD TAX | orgId={} | empId={} | FY={}",
				method, organizationId, employeeId, financialYear
		);

		OldTaxCalculationResult result =
				oldTaxCalculationService.calculateAndSaveOldTax(
						organizationId,
						employeeId,
						financialYear
				);

		log.info(
				"[{}] 📤 OLD TAX SAVE SUCCESS | empId={} | FY={} | finalTax={}",
				method,
				employeeId,
				financialYear,
				result.getTaxPayable()
		);

		return ResponseEntity.ok(result);
	}


	@PostMapping("/new/save/{employeeId}/{financialYear}")
	public ResponseEntity<Long> saveNewTaxCalculation(
	        @RequestHeader("organizationId") String organizationId,
	        @PathVariable String employeeId,
	        @PathVariable Integer financialYear) {

	    String method = "saveNewTaxCalculation";

	    log.info("[{}] 📥 Incoming request to SAVE NEW TAX | orgId={}, employeeId={}, financialYear={}",
	            method, organizationId, employeeId, financialYear);

	    Long id = newTaxCalculationService
	            .calculateAndSaveNewTax(organizationId, employeeId, financialYear);

	    log.info("[{}] 📤 NEW TAX SAVE SUCCESS | taxId={}", method, id);

	    return ResponseEntity.ok(id);
	}
	
//	@GetMapping("/calculate/poi/{employeeId}/{financialYear}")
//	public ResponseEntity<OldTaxCalculationResult> calculateOldRegimeTaxUsingPOI(
//	        @RequestHeader("organizationId") String organizationId,
//	        @PathVariable String employeeId,
//	        @PathVariable Integer financialYear
//	) {
//
//	    final String method = "calculateOldRegimeTaxUsingPOI";
//
//	    log.info("[{}] Request | orgId={} empId={} fy={}",
//	            method, organizationId, employeeId, financialYear);
//
//	    OldTaxCalculationResult result =
//	    		oldTaxCalculationService.calculateOldRegimeTaxUsingPOI(
//	                    organizationId,
//	                    employeeId,
//	                    financialYear
//	            );
//
//
//	    return ResponseEntity.ok(result);
//	}

	@PostMapping("/old/poi/calculate/{employeeId}/{financialYear}")
	public ResponseEntity<OldTaxCalculationResult> calculateOldRegimeTaxUsingPOI(
			@RequestHeader("organizationId") String organizationId,
			@PathVariable String employeeId,
			@PathVariable Integer financialYear
	) {

		OldTaxCalculationResult result =
				oldTaxCalculationService.calculateOldRegimeTaxUsingPOI(
						organizationId,
						employeeId,
						financialYear
				);

		return ResponseEntity.ok(result);
	}




	@GetMapping("/new/poi/{employeeId}/{financialYear}")
	    public ResponseEntity<NewTaxCalculationResult> calculateNewTaxWithPOI(
	            @RequestHeader("organizationId") String organizationId,
	            @PathVariable String employeeId,
	            @PathVariable Integer financialYear
	    ) {
	        NewTaxCalculationResult result =
	                newTaxCalculationService.calculateNewTaxWithPOI(organizationId, employeeId, financialYear);
	        return ResponseEntity.ok(result);
	    }


}
