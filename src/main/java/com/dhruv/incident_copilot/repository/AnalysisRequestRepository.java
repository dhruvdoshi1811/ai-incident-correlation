package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, UUID> {

    List<AnalysisRequest> findByIncidentIdOrderByRequestedAtDesc(UUID incidentId);

    Optional<AnalysisRequest> findFirstByIncidentIdAndStatusIn(UUID incidentId, List<AnalysisStatus> statuses);
}
