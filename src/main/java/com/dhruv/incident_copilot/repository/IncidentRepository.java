package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.Incident;
import com.dhruv.incident_copilot.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByStatusInAndLastAlertAttachedAtBefore(List<IncidentStatus> statuses, Instant cutoff);
}
