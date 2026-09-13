package com.itsdev.payroll.controller;

import com.itsdev.payroll.dto.action.ActionDTO;
import com.itsdev.payroll.entity.auth.Action;
import com.itsdev.payroll.service.action.ActionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listActions() {
        List<Action> actions = actionService.listAllActive();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "Actions fetched successfully");
        resp.put("data", actions);
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAction(@Valid @RequestBody ActionDTO dto) {
        Action created = actionService.createAction(dto);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 201);
        resp.put("message", "Action created");
        resp.put("data", created);
        return ResponseEntity.ok(resp);
    }
}
