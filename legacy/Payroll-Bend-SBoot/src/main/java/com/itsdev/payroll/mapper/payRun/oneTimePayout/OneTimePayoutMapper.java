//package com.itsdev.payroll.mapper.payRun.oneTimePayout;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutRequestDTO;
//import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutResponseDTO;
//import com.itsdev.payroll.entity.employee.BasicDetails;
//import com.itsdev.payroll.entity.payRun.oneTimePayout.OneTimePayout;
//import com.itsdev.payroll.entity.salarycomponents.Earning;
//
//import java.util.Collections;
//import java.util.List;
//
//public class OneTimePayoutMapper {
//
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    // ✅ Entity -> ResponseDTO
//    public static OneTimePayoutResponseDTO toResponseDTO(OneTimePayout payout) {
//        OneTimePayoutResponseDTO dto = new OneTimePayoutResponseDTO();
//        dto.setId(payout.getId());
//
//        if (payout.getEarning() != null) {
//            dto.setEarningId(payout.getEarning().getEarningId());
//            dto.setEarningName(payout.getEarning().getEarningName());
//        }
//
//        if (payout.getEmployee() != null) {
//            dto.setEmployeeId(payout.getEmployee().getId());
//            dto.setEmployeeName(
//                    payout.getEmployee().getFirstName() + " " + payout.getEmployee().getLastName()
//            );
//        }
//
//        dto.setEarningAmount(payout.getEarningAmount());
//        dto.setPayDate(payout.getPayDate());
//
//        // ✅ Deserialize taxes JSON safely
//        if (payout.getTaxes() != null) {
//            try {
//                List<String> taxesList = objectMapper.readValue(
//                        payout.getTaxes(),
//                        new TypeReference<List<String>>() {}
//                );
//                dto.setTaxes(taxesList);
//            } catch (Exception e) {
//                dto.setTaxes(Collections.emptyList()); // fallback if parsing fails
//            }
//        } else {
//            dto.setTaxes(Collections.emptyList());
//        }
//
//        dto.setCreatedAt(payout.getCreatedAt());
//        return dto;
//    }
//
//    // ✅ RequestDTO.EmployeePayoutDTO -> Entity
//    public static OneTimePayout toEntity(
//            OneTimePayoutRequestDTO.EmployeePayoutDTO dto,
//            Earning earning,
//            BasicDetails employee) {
//
//        OneTimePayout payout = new OneTimePayout();
//        payout.setEarning(earning);
//        payout.setEmployee(employee);
//        payout.setEarningAmount(dto.getEarningAmount());
//
//        if (dto.getTaxes() != null) {
//            try {
//                // ✅ Store as JSON string in DB
//                payout.setTaxes(objectMapper.writeValueAsString(dto.getTaxes()));
//            } catch (Exception e) {
//                payout.setTaxes(null);
//            }
//        }
//
//        return payout;
//    }
//}

package com.itsdev.payroll.mapper.payRun.oneTimePayout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutResponseDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.payRun.oneTimePayout.OneTimePayout;
import com.itsdev.payroll.entity.salarycomponents.Earning;

import java.util.Collections;
import java.util.List;

public class OneTimePayoutMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Entity -> ResponseDTO
    public static OneTimePayoutResponseDTO toResponseDTO(OneTimePayout payout) {
        OneTimePayoutResponseDTO dto = new OneTimePayoutResponseDTO();
        dto.setId(payout.getId());

        if (payout.getEarning() != null) {
            dto.setEarningId(payout.getEarning().getEarningId());
            dto.setEarningName(payout.getEarning().getEarningName());
        }

        if (payout.getEmployee() != null) {
            dto.setEmployeeId(payout.getEmployee().getId());
            dto.setEmployeeName(
                    payout.getEmployee().getFirstName() + " " + payout.getEmployee().getLastName()
            );
        }

        dto.setEarningAmount(payout.getEarningAmount());
        dto.setPayDate(payout.getPayDate());
        dto.setDays(payout.getDays());

        // Deserialize taxes JSON safely
        if (payout.getTaxes() != null) {
            try {
                List<String> taxesList = objectMapper.readValue(
                        payout.getTaxes(),
                        new TypeReference<List<String>>() {}
                );
                dto.setTaxes(taxesList);
            } catch (Exception e) {
                dto.setTaxes(Collections.emptyList()); // fallback if parsing fails
            }
        } else {
            dto.setTaxes(Collections.emptyList());
        }

        dto.setCreatedAt(payout.getCreatedAt());
        return dto;
    }

    // RequestDTO.EmployeePayoutDTO -> Entity
    public static OneTimePayout toEntity(
            OneTimePayoutRequestDTO.EmployeePayoutDTO dto,
            Earning earning,
            BasicDetails employee,
            java.time.LocalDate payDate
    ) {
        OneTimePayout payout = new OneTimePayout();
        payout.setEarning(earning);
        payout.setEmployee(employee);
        payout.setEarningAmount(dto.getEarningAmount());
        payout.setDays(dto.getDays());
        payout.setPayDate(payDate);

        if (dto.getTaxes() != null) {
            try {
                payout.setTaxes(objectMapper.writeValueAsString(dto.getTaxes()));
            } catch (Exception e) {
                payout.setTaxes(null);
            }
        }

        return payout;
    }
}
