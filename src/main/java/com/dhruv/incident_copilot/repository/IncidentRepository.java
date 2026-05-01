package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
