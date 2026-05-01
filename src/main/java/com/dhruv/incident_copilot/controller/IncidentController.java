package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.AlertResponse;
import com.dhruv.incident_copilot.dto.IncidentResponse;
import com.dhruv.incident_copilot.service.IncidentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<IncidentResponse> getAll() {
        return incidentService.getAll();
    }

    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable UUID id) {
        return incidentService.getById(id);
    }

    @GetMapping("/{id}/alerts")
    public List<AlertResponse> getAlerts(@PathVariable UUID id) {
        return incidentService.getAlerts(id);
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@PathVariable UUID id) {
        return incidentService.resolve(id);
    }
}
