package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.service.DemoResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class DemoResetController {

    private final DemoResetService demoResetService;

    public DemoResetController(DemoResetService demoResetService) {
        this.demoResetService = demoResetService;
    }

    @PostMapping("/reset-demo-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetDemoData() {
        demoResetService.resetDemoData();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
