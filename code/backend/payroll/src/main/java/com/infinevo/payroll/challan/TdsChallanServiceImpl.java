package com.infinevo.payroll.challan;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TdsChallanService} (W-89.1).
 */
@Service
@Transactional
public class TdsChallanServiceImpl implements TdsChallanService {

    private final TdsChallanRepository challanRepository;
    private final TdsChallanAllocationRepository allocationRepository;

    public TdsChallanServiceImpl(
            TdsChallanRepository challanRepository, TdsChallanAllocationRepository allocationRepository) {
        this.challanRepository = Objects.requireNonNull(challanRepository, "challanRepository must not be null");
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository, "allocationRepository must not be null");
    }

    @Override
    public TdsChallanResponse create(TdsChallanRequest request) {
        UUID tenantId = TenantContext.require();
        validateRequest(request);

        if (challanRepository.existsByTenantIdAndBsrCodeAndChallanSerialNoAndTenderDate(
                tenantId, request.bsrCode(), request.challanSerialNo(), request.tenderDate())) {
            throw new TdsChallanValidationException(
                    "A challan with this BSR code, serial number, and tender date already exists");
        }

        TdsChallan challan = new TdsChallan();
        challan.setTenantId(tenantId);
        challan.setFinancialYear(request.financialYear());
        challan.setQuarter(request.quarter());
        challan.setBsrCode(request.bsrCode());
        challan.setChallanSerialNo(request.challanSerialNo());
        challan.setTenderDate(request.tenderDate());
        challan.setTaxAmount(request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO);
        challan.setSurchargeAmount(request.surchargeAmount() != null ? request.surchargeAmount() : BigDecimal.ZERO);
        challan.setCessAmount(request.cessAmount() != null ? request.cessAmount() : BigDecimal.ZERO);
        challan.setInterestAmount(request.interestAmount() != null ? request.interestAmount() : BigDecimal.ZERO);
        challan.setFeePenaltyAmount(request.feePenaltyAmount() != null ? request.feePenaltyAmount() : BigDecimal.ZERO);

        BigDecimal calculatedTotal = challan.getTaxAmount()
                .add(challan.getSurchargeAmount())
                .add(challan.getCessAmount())
                .add(challan.getInterestAmount())
                .add(challan.getFeePenaltyAmount());
        challan.setTotalAmount(calculatedTotal);

        challan.setMinorHead(request.minorHead() != null ? request.minorHead() : "200");
        challan.setRemarks(request.remarks());
        challan.setFiled(false);
        challan.setCreatedAt(Instant.now());
        challan.setUpdatedAt(Instant.now());

        TdsChallan saved = challanRepository.save(challan);
        return mapToResponse(saved, BigDecimal.ZERO);
    }

    @Override
    public TdsChallanResponse update(UUID id, TdsChallanRequest request) {
        UUID tenantId = TenantContext.require();
        validateRequest(request);

        TdsChallan challan = challanRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS Challan not found: " + id));

        if (challan.isFiled()) {
            throw new TdsChallanValidationException("Filed challans cannot be modified");
        }

        challan.setFinancialYear(request.financialYear());
        challan.setQuarter(request.quarter());
        challan.setBsrCode(request.bsrCode());
        challan.setChallanSerialNo(request.challanSerialNo());
        challan.setTenderDate(request.tenderDate());
        challan.setTaxAmount(request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO);
        challan.setSurchargeAmount(request.surchargeAmount() != null ? request.surchargeAmount() : BigDecimal.ZERO);
        challan.setCessAmount(request.cessAmount() != null ? request.cessAmount() : BigDecimal.ZERO);
        challan.setInterestAmount(request.interestAmount() != null ? request.interestAmount() : BigDecimal.ZERO);
        challan.setFeePenaltyAmount(request.feePenaltyAmount() != null ? request.feePenaltyAmount() : BigDecimal.ZERO);

        BigDecimal calculatedTotal = challan.getTaxAmount()
                .add(challan.getSurchargeAmount())
                .add(challan.getCessAmount())
                .add(challan.getInterestAmount())
                .add(challan.getFeePenaltyAmount());
        challan.setTotalAmount(calculatedTotal);

        challan.setMinorHead(request.minorHead() != null ? request.minorHead() : "200");
        challan.setRemarks(request.remarks());
        challan.setUpdatedAt(Instant.now());

        TdsChallan saved = challanRepository.save(challan);
        BigDecimal allocated = calculateAllocatedTotal(tenantId, saved.getId());
        return mapToResponse(saved, allocated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TdsChallanResponse> findById(UUID id) {
        UUID tenantId = TenantContext.require();
        return challanRepository.findByTenantIdAndId(tenantId, id).map(challan -> {
            BigDecimal allocated = calculateAllocatedTotal(tenantId, challan.getId());
            return mapToResponse(challan, allocated);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsChallanResponse> list(String financialYear, String quarter) {
        UUID tenantId = TenantContext.require();
        List<TdsChallan> list;
        if (quarter != null && !quarter.isBlank()) {
            list = challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(
                    tenantId, financialYear, quarter);
        } else {
            list = challanRepository.findByTenantIdAndFinancialYearOrderByTenderDateAsc(tenantId, financialYear);
        }

        return list.stream()
                .map(challan -> {
                    BigDecimal allocated = calculateAllocatedTotal(tenantId, challan.getId());
                    return mapToResponse(challan, allocated);
                })
                .toList();
    }

    @Override
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        TdsChallan challan = challanRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS Challan not found: " + id));

        if (challan.isFiled()) {
            throw new TdsChallanValidationException("Filed challans cannot be deleted");
        }

        allocationRepository.deleteByTenantIdAndChallanId(tenantId, id);
        challanRepository.delete(challan);
    }

    @Override
    public TdsChallanResponse allocate(UUID challanId, ChallanAllocationRequest request) {
        UUID tenantId = TenantContext.require();
        TdsChallan challan = challanRepository
                .findByTenantIdAndId(tenantId, challanId)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS Challan not found: " + challanId));

        if (challan.isFiled()) {
            throw new TdsChallanValidationException("Cannot reallocate a filed challan");
        }

        if (request.employeeAllocations() != null
                && !request.employeeAllocations().isEmpty()) {
            for (ChallanAllocationRequest.EmployeeAllocationItem item : request.employeeAllocations()) {
                if (item.employeeId() == null) {
                    throw new TdsChallanValidationException("Employee ID cannot be null in allocation");
                }
                BigDecimal tax = item.taxAmount() != null ? item.taxAmount() : BigDecimal.ZERO;
                BigDecimal cess = item.cessAmount() != null ? item.cessAmount() : BigDecimal.ZERO;

                TdsChallanAllocation alloc = new TdsChallanAllocation();
                alloc.setTenantId(tenantId);
                alloc.setChallanId(challanId);
                alloc.setPayRunId(request.payRunId());
                alloc.setEmployeeId(item.employeeId());
                alloc.setAllocatedTax(tax);
                alloc.setAllocatedCess(cess);
                allocationRepository.save(alloc);
            }
        }

        BigDecimal allocated = calculateAllocatedTotal(tenantId, challanId);
        return mapToResponse(challan, allocated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdsChallanAllocation> listAllocations(UUID challanId) {
        UUID tenantId = TenantContext.require();
        return allocationRepository.findByTenantIdAndChallanId(tenantId, challanId);
    }

    private BigDecimal calculateAllocatedTotal(UUID tenantId, UUID challanId) {
        List<TdsChallanAllocation> allocs = allocationRepository.findByTenantIdAndChallanId(tenantId, challanId);
        return allocs.stream()
                .map(a -> a.getAllocatedTax().add(a.getAllocatedCess()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateRequest(TdsChallanRequest req) {
        if (req == null) {
            throw new TdsChallanValidationException("Request cannot be null");
        }
        if (req.financialYear() == null || !req.financialYear().matches("^[0-9]{4}-[0-9]{4}$")) {
            throw new TdsChallanValidationException("Financial year must match YYYY-YYYY format");
        }
        if (req.quarter() == null || !List.of("Q1", "Q2", "Q3", "Q4").contains(req.quarter())) {
            throw new TdsChallanValidationException("Quarter must be one of Q1, Q2, Q3, Q4");
        }
        if (req.bsrCode() == null || !req.bsrCode().matches("^[0-9]{7}$")) {
            throw new TdsChallanValidationException("BSR code must be exactly 7 digits");
        }
        if (req.challanSerialNo() == null || !req.challanSerialNo().matches("^[0-9]{1,5}$")) {
            throw new TdsChallanValidationException("Challan serial number must be between 1 and 5 digits");
        }
        if (req.tenderDate() == null) {
            throw new TdsChallanValidationException("Tender date is required");
        }
        if (req.minorHead() != null && !List.of("200", "400").contains(req.minorHead())) {
            throw new TdsChallanValidationException("Minor head must be either 200 or 400");
        }
    }

    private TdsChallanResponse mapToResponse(TdsChallan c, BigDecimal allocated) {
        BigDecimal total = c.getTotalAmount() != null ? c.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal unallocated = total.subtract(allocated);
        return new TdsChallanResponse(
                c.getId(),
                c.getTenantId(),
                c.getFinancialYear(),
                c.getQuarter(),
                c.getBsrCode(),
                c.getChallanSerialNo(),
                c.getTenderDate(),
                c.getTaxAmount(),
                c.getSurchargeAmount(),
                c.getCessAmount(),
                c.getInterestAmount(),
                c.getFeePenaltyAmount(),
                total,
                allocated,
                unallocated,
                c.getMinorHead(),
                c.isFiled(),
                c.getRemarks(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
