package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.NotificationResponse;
import com.dhruv.incident_copilot.entity.NotificationStatus;
import com.dhruv.incident_copilot.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/incidents/{id}/notifications")
    public List<NotificationResponse> getByIncidentId(@PathVariable UUID id) {
        return notificationService.getByIncidentId(id);
    }

    @GetMapping("/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public List<NotificationResponse> getByStatus(@RequestParam NotificationStatus status) {
        return notificationService.getByStatus(status);
    }

    @PostMapping("/admin/notifications/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationResponse retry(@PathVariable UUID id) {
        return notificationService.retry(id);
    }
}
