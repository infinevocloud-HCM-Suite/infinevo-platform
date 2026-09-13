package com.itsdev.payroll.repository.statutorycomponents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itsdev.payroll.entity.statutorycomponents.PTHistory;


@Repository
public interface PTHistoryRepository extends JpaRepository<PTHistory, Long> {

}
