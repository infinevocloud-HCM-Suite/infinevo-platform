package com.itsdev.payroll.repository.employeeitdeclaration;

import com.itsdev.payroll.entity.EmployeeITDeclaration.Section6AItemMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Section6AItemMasterRepository
        extends JpaRepository<Section6AItemMaster, Long> {

    List<Section6AItemMaster> findByIsActiveTrue();
}
