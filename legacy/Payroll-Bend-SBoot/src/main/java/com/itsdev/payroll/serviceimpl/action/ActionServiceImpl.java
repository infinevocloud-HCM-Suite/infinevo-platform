package com.itsdev.payroll.serviceimpl.action;

import com.itsdev.payroll.dto.action.ActionDTO;
import com.itsdev.payroll.entity.auth.Action;
import com.itsdev.payroll.repository.auth.ActionRepository;
import com.itsdev.payroll.service.action.ActionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ActionServiceImpl implements ActionService {

    private final ActionRepository actionRepository;

    public ActionServiceImpl(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Override
    @Transactional
    public Action createAction(ActionDTO dto) {
        Action a = new Action();
        a.setCode(dto.getCode());
        a.setLabel(dto.getLabel());
        a.setCategory(dto.getCategory());
        a.setEntityKey(dto.getEntityKey());
        a.setDescription(dto.getDescription());
        a.setActive(dto.getActive() == null ? true : dto.getActive());
        a.setCreatedBy(dto.getCreatedBy());
        return actionRepository.save(a);
    }

    @Override
    public List<Action> listAllActive() {
        return actionRepository.findAllByActiveTrue();
    }

    @Override
    public Optional<Action> findByCode(String code) {
        return actionRepository.findByCode(code);
    }

    @Override
    public Optional<Action> findById(Long id) {
        return actionRepository.findById(id);
    }
}
