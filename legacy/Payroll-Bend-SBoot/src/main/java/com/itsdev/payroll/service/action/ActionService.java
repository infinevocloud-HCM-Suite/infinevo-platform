package com.itsdev.payroll.service.action;

import com.itsdev.payroll.dto.action.ActionDTO;
import com.itsdev.payroll.entity.auth.Action;

import java.util.List;
import java.util.Optional;

public interface ActionService {
    Action createAction(ActionDTO dto);

    List<Action> listAllActive();

    Optional<Action> findByCode(String code);

    Optional<Action> findById(Long id);
}
