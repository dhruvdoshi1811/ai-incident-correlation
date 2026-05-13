package com.dhruv.incident_copilot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class MockWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MockWebhookController.class);

    @PostMapping("/mock-webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload) {
        log.info("Mock webhook received: {}", payload);
        return ResponseEntity.ok().build();
    }
}
