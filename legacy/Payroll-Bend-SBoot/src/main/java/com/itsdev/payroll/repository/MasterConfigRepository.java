package com.itsdev.payroll.repository;


import com.itsdev.payroll.entity.MasterConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterConfigRepository extends JpaRepository<MasterConfig, Long> {
    Optional<MasterConfig> findByComponentName(String componentName);
}
