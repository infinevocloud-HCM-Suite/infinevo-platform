package com.itsdev.payroll.mapper.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.*;
import com.itsdev.payroll.entity.EmployeeITDeclaration.*;

import java.util.*;
import java.util.stream.Collectors;

public class EmployeeInvestmentDeclarationMapper {

    private EmployeeInvestmentDeclarationMapper() {}


    public static EmployeeInvestmentDeclarationDTO toDTO(EmployeeInvestmentDeclaration entity) {
        if (entity == null) return null;

        EmployeeInvestmentDeclarationDTO dto = new EmployeeInvestmentDeclarationDTO();

        // ---------- Header ----------
        dto.setFiscalYear(entity.getFiscalYear());
        dto.setDeclarationTaxYearStart(entity.getDeclarationTaxYearStart());
        dto.setDeclarationTaxYearEnd(entity.getDeclarationTaxYearEnd());
        dto.setCurrentTaxYearStart(entity.getCurrentTaxYearStart());
        dto.setCurrentTaxYearEnd(entity.getCurrentTaxYearEnd());

        dto.setCanAllowEdit(entity.getCanAllowEdit());
        dto.setTaxRegime(entity.getTaxRegime());
        dto.setTaxRegimeFormatted(entity.getTaxRegimeFormatted());
        dto.setIsMultipleTaxRegimesApplicable(entity.getIsMultipleTaxRegimesApplicable());
        dto.setIsLenderpanMandatory(entity.getIsLenderPanMandatory());
        dto.setCanChangeTaxRegime(entity.getCanChangeTaxRegime());

        dto.setIsEmployeeRehiredOnSameTaxyear(null); // unused but included for UI
        dto.setIsStayingInRentedHouse(entity.getIsStayingInRentedHouse());
        dto.setIsRepayingSelfOccupiedLoan(entity.getIsRepayingSelfOccupiedLoan());
        dto.setHasLetOutProperty(entity.getHasLetOutProperty());

        dto.setStatus(entity.getStatus());
        dto.setStatusFormatted(entity.getStatusFormatted());
        dto.setMessageTypes(entity.getMessageTypes());
        dto.setTaxPlanCount(entity.getTaxPlanCount());

        // ---------- Children ----------
        dto.setHouseRentDeclarations(mapHouseRents(entity.getHouseRents()));
        dto.setOtherIncomesDeclarations(mapOtherIncomes(entity.getOtherIncomes()));
        dto.setPreviousEmploymentDeclarations(mapPrevEmployment(entity.getPrevEmploymentDeclarations()));
        dto.setLetOutPropertyDeclarations(mapLetOutProperties(entity.getLetOutProperties()));
        dto.setSection6aDeclarations(mapSection6ADeclarations(entity.getSection6aDeclarations()));
        dto.setSection6aPreTaxDeductionsItems(mapPreTaxDeductions(entity.getPreTaxDeductions()));
        dto.setTaxSummaries(mapTaxSummaries(entity.getTaxSummaries()));
        dto.setHomeLoanDeclarations(mapHomeLoans(entity.getHomeLoans()));


        // filled by service from master tables
        dto.setSection6aItems(Collections.emptyList()); 
        dto.setSection6a80cDetails(null);
        dto.setSection6a80dDetails(null);
        dto.setItExcludeItemsInPortal(Collections.emptyList());

        return dto;
    }

    // ---------- Individual Child → DTO Mappers ----------

    private static List<HouseRentDTO> mapHouseRents(List<EmployeeInvHouseRent> rents) {
        if (rents == null) return Collections.emptyList();
        return rents.stream()
                .filter(Objects::nonNull)
                .map(EmployeeInvestmentDeclarationMapper::toHouseRentDTO)
                .collect(Collectors.toList());
    }

    private static HouseRentDTO toHouseRentDTO(EmployeeInvHouseRent e) {
        HouseRentDTO dto = new HouseRentDTO();
        dto.setId(e.getId());
        dto.setFromMonth(e.getFromMonth());
        dto.setToMonth(e.getToMonth());
        dto.setAddress(e.getAddress());
        dto.setLandlordName(e.getLandlordName());
        dto.setLandlordPan(e.getLandlordPan());
        dto.setIsMetro(e.getIsMetro());
        dto.setAmountPerMonth(e.getAmountPerMonth());
        dto.setAmountPerMonthFormatted(null);
        dto.setCurrency(e.getCurrency());
        dto.setItemIdExternal(e.getItemIdExternal());
        return dto;
    }

    private static List<OtherIncomeDTO> mapOtherIncomes(List<EmployeeInvOtherIncome> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toOtherIncomeDTO).toList();
    }

    private static OtherIncomeDTO toOtherIncomeDTO(EmployeeInvOtherIncome e) {
        OtherIncomeDTO dto = new OtherIncomeDTO();
        dto.setId(e.getId());
        dto.setType(e.getType());
        dto.setTypeFormatted(e.getTypeFormatted());
        dto.setName(e.getName());
        dto.setAmount(e.getAmount());
        dto.setAmountFormatted(null);
        dto.setDeclaredAmount(e.getDeclaredAmount());
        dto.setDeclaredAmountFormatted(null);
        dto.setCanEditInPortal(e.getCanEditInPortal());
        dto.setNameOfLender(e.getNameOfLender());
        dto.setPanOfLender(e.getPanOfLender());
        dto.setItemIdExternal(e.getItemIdExternal());
        return dto;
    }

    private static List<PrevEmploymentDTO> mapPrevEmployment(List<EmployeeInvPrevEmployment> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toPrevEmploymentDTO).toList();
    }

    private static PrevEmploymentDTO toPrevEmploymentDTO(EmployeeInvPrevEmployment e) {
        PrevEmploymentDTO dto = new PrevEmploymentDTO();
        dto.setId(e.getId());
        dto.setType(e.getType());
        dto.setTypeFormatted(e.getTypeFormatted());
        dto.setName(e.getName());
        dto.setAmount(e.getAmount());
        dto.setAmountFormatted(null);
        dto.setDeclaredAmount(e.getDeclaredAmount());
        dto.setDeclaredAmountFormatted(null);
        dto.setCanEditInPortal(e.getCanEditInPortal());
        dto.setItemIdExternal(e.getItemIdExternal());
        return dto;
    }

    private static List<LetOutPropertyDTO> mapLetOutProperties(List<EmployeeInvLetOutProperty> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toLetOutPropertyDTO).toList();
    }

    private static LetOutPropertyDTO toLetOutPropertyDTO(EmployeeInvLetOutProperty e) {
        LetOutPropertyDTO dto = new LetOutPropertyDTO();
        dto.setId(e.getId());
        dto.setPropertyName(e.getPropertyName());
        dto.setAddress(e.getAddress());
        dto.setNetIncomeLoss(e.getNetIncomeLoss());
        dto.setNetIncomeLossFormatted(null);
        dto.setItemIdExternal(e.getItemIdExternal());
        dto.setPropertyDetails(mapLetOutPropertyDetails(e.getPropertyDetails()));
        return dto;
    }

    private static List<LetOutPropertyDetailDTO> mapLetOutPropertyDetails(List<EmployeeInvLetOutPropertyDetail> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toLetOutPropertyDetailDTO).toList();
    }

    private static LetOutPropertyDetailDTO toLetOutPropertyDetailDTO(EmployeeInvLetOutPropertyDetail e) {
        LetOutPropertyDetailDTO dto = new LetOutPropertyDetailDTO();
        dto.setId(e.getId());
        dto.setType(e.getType());
        dto.setAmount(e.getAmount());
        dto.setAmountFormatted(null);
        dto.setNameOfLender(e.getNameOfLender());
        dto.setPanOfLender(e.getPanOfLender());
        return dto;
    }

    private static List<Section6ADeclarationDTO> mapSection6ADeclarations(List<EmployeeInvSection6A> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toSection6ADeclarationDTO).toList();
    }

    private static Section6ADeclarationDTO toSection6ADeclarationDTO(EmployeeInvSection6A e) {
        Section6ADeclarationDTO dto = new Section6ADeclarationDTO();
        dto.setId(e.getId());
        dto.setSection6aItemId(e.getSection6aItemId());
        dto.setCategory(e.getCategory());
        dto.setCategoryFormatted(e.getCategoryFormatted());
        dto.setType(e.getType());
        dto.setTypeFormatted(e.getTypeFormatted());
        dto.setAmount(e.getAmount());
        dto.setAmountFormatted(null);
        dto.setItemIdExternal(e.getItemIdExternal());
        return dto;
    }

    private static List<PreTaxDeductionItemDTO> mapPreTaxDeductions(List<EmployeeInvPreTaxDeduction> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toPreTaxDeductionDTO).toList();
    }

    private static PreTaxDeductionItemDTO toPreTaxDeductionDTO(EmployeeInvPreTaxDeduction e) {
        PreTaxDeductionItemDTO dto = new PreTaxDeductionItemDTO();
        dto.setId(e.getId());
        dto.setCodeString(e.getCodeString());
        dto.setCategory(e.getCategory());
        dto.setCategoryFormatted(e.getCategoryFormatted());
        dto.setType(e.getType());
        dto.setTypeFormatted(e.getTypeFormatted());
        dto.setAmount(e.getAmount());
        dto.setAmountFormatted(e.getAmountFormatted());
        dto.setInvestmentAmount(e.getInvestmentAmount());
        dto.setInvestmentAmountFormatted(e.getInvestmentAmountFormatted());
        return dto;
    }

    private static List<TaxSummaryDTO> mapTaxSummaries(List<EmployeeInvTaxSummary> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(EmployeeInvestmentDeclarationMapper::toTaxSummaryDTO).toList();
    }

    private static TaxSummaryDTO toTaxSummaryDTO(EmployeeInvTaxSummary e) {
        TaxSummaryDTO dto = new TaxSummaryDTO();
        dto.setId(e.getId());
        dto.setRegime(e.getRegime());
        dto.setTaxYearStart(e.getTaxYearStart());
        dto.setTaxYearEnd(e.getTaxYearEnd());
        dto.setTaxableIncome(e.getTaxableIncome());
        dto.setTaxableIncomeFormatted(e.getTaxableIncomeFormatted());
        dto.setNetTaxableIncome(e.getNetTaxableIncome());
        dto.setNetTaxableIncomeFormatted(e.getNetTaxableIncomeFormatted());
        dto.setTaxOnTaxableIncome(e.getTaxOnTaxableIncome());
        dto.setTaxOnTaxableIncomeFormatted(e.getTaxOnTaxableIncomeFormatted());
        dto.setTaxYtdAmount(e.getTaxYtdAmount());
        dto.setTaxYtdAmountFormatted(e.getTaxYtdAmountFormatted());
        dto.setTaxToBePaid(e.getTaxToBePaid());
        dto.setTaxToBePaidFormatted(e.getTaxToBePaidFormatted());
        dto.setTdsThroughPayroll(e.getTdsThroughPayroll());
        dto.setTdsPreviousEmployer(e.getTdsPreviousEmployer());
        dto.setTdsOtherIncome(e.getTdsOtherIncome());
        dto.setOtherSourcesIncome(e.getOtherSourcesIncome());
        dto.setExemptionUnderSection10(e.getExemptionUnderSection10());
        dto.setExemptionUnderSection6A(e.getExemptionUnderSection6A());
        dto.setNoOfRemainingMonths(e.getNoOfRemainingMonths());
        return dto;
    }


    // ---------- Home Loan (Self Occupied) ----------

private static List<EmployeeHomeLoanDTO> mapHomeLoans(
        List<EmployeeInvHomeLoan> list
) {
    if (list == null) return Collections.emptyList();

    return list.stream()
            .filter(Objects::nonNull)
            .map(EmployeeInvestmentDeclarationMapper::toHomeLoanDTO)
            .collect(Collectors.toList());
}

private static EmployeeHomeLoanDTO toHomeLoanDTO(EmployeeInvHomeLoan e) {
    EmployeeHomeLoanDTO dto = new EmployeeHomeLoanDTO();

    dto.setId(e.getId());
    dto.setPrincipalPaid(e.getPrincipalPaid());
    dto.setInterestPaid(e.getInterestPaid());
    dto.setLenderName(e.getLenderName());
    dto.setLenderPan(e.getLenderPan());
    dto.setItemIdExternal(e.getItemIdExternal());

    return dto;
}



    public static void updateEntityFromDTO(EmployeeInvestmentDeclarationDTO dto,
                                           EmployeeInvestmentDeclaration entity) {

        if (dto == null || entity == null)
            return;

        // ---------- Header ----------
        entity.setFiscalYear(dto.getFiscalYear());
        entity.setDeclarationTaxYearStart(dto.getDeclarationTaxYearStart());
        entity.setDeclarationTaxYearEnd(dto.getDeclarationTaxYearEnd());
        entity.setCurrentTaxYearStart(dto.getCurrentTaxYearStart());
        entity.setCurrentTaxYearEnd(dto.getCurrentTaxYearEnd());
        entity.setCanAllowEdit(dto.getCanAllowEdit());
        entity.setTaxRegime(dto.getTaxRegime());
        entity.setTaxRegimeFormatted(dto.getTaxRegimeFormatted());
        entity.setIsMultipleTaxRegimesApplicable(dto.getIsMultipleTaxRegimesApplicable());
        entity.setIsLenderPanMandatory(dto.getIsLenderpanMandatory());
        entity.setCanChangeTaxRegime(dto.getCanChangeTaxRegime());
        entity.setIsStayingInRentedHouse(dto.getIsStayingInRentedHouse());
        entity.setIsRepayingSelfOccupiedLoan(dto.getIsRepayingSelfOccupiedLoan());
        entity.setHasLetOutProperty(dto.getHasLetOutProperty());
        entity.setStatus(dto.getStatus());
        entity.setStatusFormatted(dto.getStatusFormatted());
        entity.setMessageTypes(dto.getMessageTypes());
        entity.setTaxPlanCount(dto.getTaxPlanCount());

        // ---------- MERGE Child Lists ----------
        mergeHouseRents(dto.getHouseRentDeclarations(), entity);
        mergeOtherIncomes(dto.getOtherIncomesDeclarations(), entity);
        mergePrevEmployment(dto.getPreviousEmploymentDeclarations(), entity);
        mergeLetOutProperties(dto.getLetOutPropertyDeclarations(), entity);
        mergeSection6A(dto.getSection6aDeclarations(), entity);
        mergePreTaxDeductions(dto.getSection6aPreTaxDeductionsItems(), entity);
        mergeTaxSummaries(dto.getTaxSummaries(), entity);
        mergeHomeLoans(dto.getHomeLoanDeclarations(), entity);
    }

    // ============================================================
    //                 MERGE HELPERS (Option-B)
    // ============================================================

    // ---------- House Rent ----------
    private static void mergeHouseRents(List<HouseRentDTO> list, EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvHouseRent> existing =
                parent.getHouseRents().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvHouseRent::getId, e -> e));

        List<EmployeeInvHouseRent> newList = new ArrayList<>();

        if (list != null) {
            for (HouseRentDTO dto : list) {
                EmployeeInvHouseRent e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvHouseRent();

                e.setDeclaration(parent);
                e.setFromMonth(dto.getFromMonth());
                e.setToMonth(dto.getToMonth());
                e.setAddress(dto.getAddress());
                e.setLandlordName(dto.getLandlordName());
                e.setLandlordPan(dto.getLandlordPan());
                e.setIsMetro(dto.getIsMetro());
                e.setAmountPerMonth(dto.getAmountPerMonth());
                e.setCurrency(dto.getCurrency());
                e.setItemIdExternal(dto.getItemIdExternal());

                newList.add(e);
            }
        }

        parent.getHouseRents().clear();
        parent.getHouseRents().addAll(newList);
    }

    // ---------- Other Incomes ----------
    private static void mergeOtherIncomes(List<OtherIncomeDTO> list, EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvOtherIncome> existing =
                parent.getOtherIncomes().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvOtherIncome::getId, e -> e));

        List<EmployeeInvOtherIncome> newList = new ArrayList<>();

        if (list != null) {
            for (OtherIncomeDTO dto : list) {
                EmployeeInvOtherIncome e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvOtherIncome();

                e.setDeclaration(parent);
                e.setType(dto.getType());
                e.setTypeFormatted(dto.getTypeFormatted());
                e.setName(dto.getName());
                e.setAmount(dto.getAmount());
                e.setDeclaredAmount(dto.getDeclaredAmount());
                e.setCanEditInPortal(dto.getCanEditInPortal());
                e.setNameOfLender(dto.getNameOfLender());
                e.setPanOfLender(dto.getPanOfLender());
                e.setItemIdExternal(dto.getItemIdExternal());

                newList.add(e);
            }
        }

        parent.getOtherIncomes().clear();
        parent.getOtherIncomes().addAll(newList);
    }

    // ---------- Previous Employment ----------
    private static void mergePrevEmployment(List<PrevEmploymentDTO> list, EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvPrevEmployment> existing =
                parent.getPrevEmploymentDeclarations().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvPrevEmployment::getId, e -> e));

        List<EmployeeInvPrevEmployment> newList = new ArrayList<>();

        if (list != null) {
            for (PrevEmploymentDTO dto : list) {
                EmployeeInvPrevEmployment e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvPrevEmployment();

                e.setDeclaration(parent);
                e.setType(dto.getType());
                e.setTypeFormatted(dto.getTypeFormatted());
                e.setName(dto.getName());
                e.setAmount(dto.getAmount());
                e.setDeclaredAmount(dto.getDeclaredAmount());
                e.setCanEditInPortal(dto.getCanEditInPortal());
                e.setItemIdExternal(dto.getItemIdExternal());

                newList.add(e);
            }
        }

        parent.getPrevEmploymentDeclarations().clear();
        parent.getPrevEmploymentDeclarations().addAll(newList);
    }

    // ---------- Let-Out Properties + Details ----------
    private static void mergeLetOutProperties(List<LetOutPropertyDTO> list, EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvLetOutProperty> existing =
                parent.getLetOutProperties().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvLetOutProperty::getId, e -> e));

        List<EmployeeInvLetOutProperty> newList = new ArrayList<>();

        if (list != null) {
            for (LetOutPropertyDTO dto : list) {
                EmployeeInvLetOutProperty e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvLetOutProperty();

                e.setDeclaration(parent);
                e.setPropertyName(dto.getPropertyName());
                e.setAddress(dto.getAddress());
                e.setNetIncomeLoss(dto.getNetIncomeLoss());
                e.setItemIdExternal(dto.getItemIdExternal());

                // merge nested list
                mergeLetOutPropertyDetails(dto.getPropertyDetails(), e);

                newList.add(e);
            }
        }

        parent.getLetOutProperties().clear();
        parent.getLetOutProperties().addAll(newList);
    }

    private static void mergeLetOutPropertyDetails(List<LetOutPropertyDetailDTO> list,
                                                   EmployeeInvLetOutProperty parentProp) {

        Map<Long, EmployeeInvLetOutPropertyDetail> existing =
                parentProp.getPropertyDetails().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvLetOutPropertyDetail::getId, e -> e));

        List<EmployeeInvLetOutPropertyDetail> newList = new ArrayList<>();

        if (list != null) {
            for (LetOutPropertyDetailDTO dto : list) {
                EmployeeInvLetOutPropertyDetail e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvLetOutPropertyDetail();

                e.setLetOutProperty(parentProp);
                e.setType(dto.getType());
                e.setAmount(dto.getAmount());
                e.setNameOfLender(dto.getNameOfLender());
                e.setPanOfLender(dto.getPanOfLender());

                newList.add(e);
            }
        }

        parentProp.getPropertyDetails().clear();
        parentProp.getPropertyDetails().addAll(newList);
    }

    // ---------- Section 6A ----------
    private static void mergeSection6A(List<Section6ADeclarationDTO> list,
                                       EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvSection6A> existing =
                parent.getSection6aDeclarations().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvSection6A::getId, e -> e));

        List<EmployeeInvSection6A> newList = new ArrayList<>();

        if (list != null) {
            for (Section6ADeclarationDTO dto : list) {
                EmployeeInvSection6A e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvSection6A();

                e.setDeclaration(parent);
                e.setSection6aItemId(dto.getSection6aItemId());
                e.setCategory(dto.getCategory());
                e.setCategoryFormatted(dto.getCategoryFormatted());
                e.setType(dto.getType());
                e.setTypeFormatted(dto.getTypeFormatted());
                e.setAmount(dto.getAmount());
                e.setItemIdExternal(dto.getItemIdExternal());

                newList.add(e);
            }
        }

        parent.getSection6aDeclarations().clear();
        parent.getSection6aDeclarations().addAll(newList);
    }

    // ---------- Pre-Tax Deductions ----------
    private static void mergePreTaxDeductions(List<PreTaxDeductionItemDTO> list,
                                              EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvPreTaxDeduction> existing =
                parent.getPreTaxDeductions().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvPreTaxDeduction::getId, e -> e));

        List<EmployeeInvPreTaxDeduction> newList = new ArrayList<>();

        if (list != null) {
            for (PreTaxDeductionItemDTO dto : list) {
                EmployeeInvPreTaxDeduction e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvPreTaxDeduction();

                e.setDeclaration(parent);
                e.setCodeString(dto.getCodeString());
                e.setCategory(dto.getCategory());
                e.setCategoryFormatted(dto.getCategoryFormatted());
                e.setType(dto.getType());
                e.setTypeFormatted(dto.getTypeFormatted());
                e.setAmount(dto.getAmount());
                e.setAmountFormatted(dto.getAmountFormatted());
                e.setInvestmentAmount(dto.getInvestmentAmount());
                e.setInvestmentAmountFormatted(dto.getInvestmentAmountFormatted());

                newList.add(e);
            }
        }

        parent.getPreTaxDeductions().clear();
        parent.getPreTaxDeductions().addAll(newList);
    }

    // ---------- Tax Summary ----------
    private static void mergeTaxSummaries(List<TaxSummaryDTO> list,
                                          EmployeeInvestmentDeclaration parent) {

        Map<Long, EmployeeInvTaxSummary> existing =
                parent.getTaxSummaries().stream()
                        .filter(e -> e.getId() != null)
                        .collect(Collectors.toMap(EmployeeInvTaxSummary::getId, e -> e));

        List<EmployeeInvTaxSummary> newList = new ArrayList<>();

        if (list != null) {
            for (TaxSummaryDTO dto : list) {
                EmployeeInvTaxSummary e =
                        (dto.getId() != null && existing.containsKey(dto.getId()))
                                ? existing.get(dto.getId()) : new EmployeeInvTaxSummary();

                e.setDeclaration(parent);
                e.setRegime(dto.getRegime());
                e.setTaxYearStart(dto.getTaxYearStart());
                e.setTaxYearEnd(dto.getTaxYearEnd());

                e.setTaxableIncome(dto.getTaxableIncome());
                e.setTaxableIncomeFormatted(dto.getTaxableIncomeFormatted());

                e.setNetTaxableIncome(dto.getNetTaxableIncome());
                e.setNetTaxableIncomeFormatted(dto.getNetTaxableIncomeFormatted());

                e.setTaxOnTaxableIncome(dto.getTaxOnTaxableIncome());
                e.setTaxOnTaxableIncomeFormatted(dto.getTaxOnTaxableIncomeFormatted());

                e.setTaxYtdAmount(dto.getTaxYtdAmount());
                e.setTaxYtdAmountFormatted(dto.getTaxYtdAmountFormatted());

                e.setTaxToBePaid(dto.getTaxToBePaid());
                e.setTaxToBePaidFormatted(dto.getTaxToBePaidFormatted());

                e.setTdsThroughPayroll(dto.getTdsThroughPayroll());
                e.setTdsPreviousEmployer(dto.getTdsPreviousEmployer());
                e.setTdsOtherIncome(dto.getTdsOtherIncome());

                e.setOtherSourcesIncome(dto.getOtherSourcesIncome());
                e.setExemptionUnderSection10(dto.getExemptionUnderSection10());
                e.setExemptionUnderSection6A(dto.getExemptionUnderSection6A());

                e.setNoOfRemainingMonths(dto.getNoOfRemainingMonths());

                newList.add(e);
            }
        }

        parent.getTaxSummaries().clear();
        parent.getTaxSummaries().addAll(newList);
    }


        private static void mergeHomeLoans(
        List<EmployeeHomeLoanDTO> list,
        EmployeeInvestmentDeclaration parent
) {

    Map<Long, EmployeeInvHomeLoan> existing =
            parent.getHomeLoans().stream()
                    .filter(e -> e.getId() != null)
                    .collect(Collectors.toMap(EmployeeInvHomeLoan::getId, e -> e));

    List<EmployeeInvHomeLoan> newList = new ArrayList<>();

    if (list != null) {
        for (EmployeeHomeLoanDTO dto : list) {

            EmployeeInvHomeLoan e =
                    (dto.getId() != null && existing.containsKey(dto.getId()))
                            ? existing.get(dto.getId())
                            : new EmployeeInvHomeLoan();

            e.setDeclaration(parent);
            e.setPrincipalPaid(dto.getPrincipalPaid());
            e.setInterestPaid(dto.getInterestPaid());
            e.setLenderName(dto.getLenderName());
            e.setLenderPan(dto.getLenderPan());
            e.setItemIdExternal(dto.getItemIdExternal());

            newList.add(e);
        }
    }

    parent.getHomeLoans().clear();
    parent.getHomeLoans().addAll(newList);
}



    /**
 *  Used ONLY for EMPLOYEE POST / PUT APIs
 * Accepts ONLY editable fields from UI
 */
public static void updateEntityFromRequestDTO(
        EmployeeInvestmentDeclarationRequestDTO dto,
        EmployeeInvestmentDeclaration entity
)
 {
    if (dto == null || entity == null) return;

    // ---------- Editable Header Fields ONLY ----------
    entity.setTaxRegime(dto.getTaxRegime());
    entity.setIsStayingInRentedHouse(dto.getIsStayingInRentedHouse());
    entity.setIsRepayingSelfOccupiedLoan(dto.getIsRepayingSelfOccupiedLoan());
    entity.setHasLetOutProperty(dto.getHasLetOutProperty());

    // ---------- Editable Child Lists ONLY ----------
    mergeHouseRents(dto.getHouseRentDeclarations(), entity);
    mergeSection6A(dto.getSection6aDeclarations(), entity);
    mergePrevEmployment(dto.getPreviousEmploymentDeclarations(), entity);
    mergeOtherIncomes(dto.getOtherIncomesDeclarations(), entity);
    mergeLetOutProperties(dto.getLetOutPropertyDeclarations(), entity);
    mergeHomeLoans(dto.getHomeLoanDeclarations(), entity);


}


}
