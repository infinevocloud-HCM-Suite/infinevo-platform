package com.itsdev.payroll.repository.auth;

import com.itsdev.payroll.entity.auth.Action;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ActionRepository extends JpaRepository<Action, Long> {
    Optional<Action> findByCode(String code);

    List<Action> findAllByActiveTrue();
}
