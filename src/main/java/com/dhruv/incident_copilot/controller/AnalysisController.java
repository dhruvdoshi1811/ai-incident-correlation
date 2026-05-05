package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/incidents/{id}/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analysisService.submit(id));
    }

    @GetMapping("/analysis/{id}")
    public AnalysisResponse getById(@PathVariable UUID id) {
        return analysisService.getById(id);
    }

    @GetMapping("/incidents/{id}/analysis")
    public List<AnalysisResponse> getByIncidentId(@PathVariable UUID id) {
        return analysisService.getByIncidentId(id);
    }
}
