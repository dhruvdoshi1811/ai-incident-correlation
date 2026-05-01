package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.AlertStormResult;
import com.dhruv.incident_copilot.service.DemoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/simulate-alert-storm")
    @PreAuthorize("hasRole('ADMIN')")
    public AlertStormResult simulateAlertStorm(@RequestParam(defaultValue = "10") int count) {
        return demoService.simulateAlertStorm(count);
    }
}
