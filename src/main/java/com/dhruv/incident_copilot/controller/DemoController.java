package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.AlertStormResult;
import com.dhruv.incident_copilot.dto.ScenarioResponse;
import com.dhruv.incident_copilot.service.DemoService;
import com.dhruv.incident_copilot.service.IncidentScenario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/scenarios")
    public List<ScenarioResponse> getScenarios() {
        return Arrays.stream(IncidentScenario.values())
                .map(s -> new ScenarioResponse(s.name(), s.displayName(), s.description()))
                .toList();
    }

    @PostMapping("/simulate-alert-storm")
    @PreAuthorize("hasRole('ADMIN')")
    public AlertStormResult simulateAlertStorm(
            @RequestParam IncidentScenario scenario,
            @RequestParam(defaultValue = "10") int count) {
        return demoService.simulateAlertStorm(scenario, count);
    }
}
