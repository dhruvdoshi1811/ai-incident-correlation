package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AlertRequest;
import com.dhruv.incident_copilot.dto.AlertStormResult;
import com.dhruv.incident_copilot.entity.Severity;
import com.dhruv.incident_copilot.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DemoService {

    private static final Duration CORRELATION_POLL_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CORRELATION_POLL_INTERVAL = Duration.ofMillis(250);

    private final AlertService alertService;
    private final AlertRepository alertRepository;

    public DemoService(AlertService alertService, AlertRepository alertRepository) {
        this.alertService = alertService;
        this.alertRepository = alertRepository;
    }

    public AlertStormResult simulateAlertStorm(int alertCount) {
        String stormId = UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch startingGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(alertCount);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(alertCount, 20));
        List<UUID> alertIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        IntStream.range(0, alertCount).forEach(i -> executor.submit(() -> {
            try {
                startingGate.await();
                AlertRequest request = new AlertRequest(
                        "datadog",
                        "storm-" + stormId + "-" + i,
                        Severity.CRITICAL,
                        "High CPU usage detected on production host",
                        "{\"cpu\":98,\"host\":\"host-" + i + "\"}");
                var result = alertService.ingest(request);
                alertIds.add(result.alert().id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completionGate.countDown();
            }
        }));

        startingGate.countDown();
        try {
            completionGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        Set<UUID> resultingIncidentIds = awaitCorrelation(alertIds);
        return new AlertStormResult(alertCount, resultingIncidentIds.size() == 1, List.copyOf(resultingIncidentIds));
    }

    private Set<UUID> awaitCorrelation(List<UUID> alertIds) {
        Instant deadline = Instant.now().plus(CORRELATION_POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            var alerts = alertRepository.findAllById(alertIds);
            boolean allAssigned = alerts.size() == alertIds.size()
                    && alerts.stream().allMatch(a -> a.getIncidentId() != null);
            if (allAssigned) {
                return alerts.stream().map(a -> a.getIncidentId()).collect(Collectors.toSet());
            }
            try {
                Thread.sleep(CORRELATION_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return alertRepository.findAllById(alertIds).stream()
                .map(a -> a.getIncidentId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
