package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AlertResponse;
import com.dhruv.incident_copilot.service.AlertIngestResult;
import com.dhruv.incident_copilot.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> ingest(@Valid @RequestBody AlertRequest request) {
        AlertIngestResult result = alertService.ingest(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.alert());
    }

    @GetMapping("/{id}")
    public AlertResponse getById(@PathVariable UUID id) {
        return alertService.getById(id);
    }

    @GetMapping
    public List<AlertResponse> getByIncidentId(@RequestParam UUID incidentId) {
        return alertService.getByIncidentId(incidentId);
    }
}
