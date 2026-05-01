package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.event.AlertIngestedEvent;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertIngestWriter {

    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AlertIngestWriter(AlertRepository alertRepository, ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert insert(AlertRequest request) {
        Alert alert = new Alert();
        alert.setSourceSystem(request.sourceSystem());
        alert.setExternalAlertId(request.externalAlertId());
        alert.setSeverity(request.severity());
        alert.setTitle(request.title());
        alert.setRawPayload(request.rawPayload());

        alertRepository.save(alert);
        eventPublisher.publishEvent(new AlertIngestedEvent(alert.getId()));
        return alert;
    }
}
