package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Optional<Alert> findBySourceSystemAndExternalAlertId(String sourceSystem, String externalAlertId);

    List<Alert> findByIncidentId(UUID incidentId);
}
