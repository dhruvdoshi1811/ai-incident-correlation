package com.dhruv.incident_copilot.event;

import com.dhruv.incident_copilot.service.CorrelationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CorrelationEventListener {

    private final CorrelationService correlationService;

    public CorrelationEventListener(CorrelationService correlationService) {
        this.correlationService = correlationService;
    }

    @Async("correlationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertIngested(AlertIngestedEvent event) {
        correlationService.correlate(event.alertId());
    }
}
