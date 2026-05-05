package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AnalysisResponse;
import com.dhruv.incident_copilot.entity.AnalysisRequest;
import com.dhruv.incident_copilot.entity.AnalysisStatus;
import com.dhruv.incident_copilot.exception.ResourceNotFoundException;
import com.dhruv.incident_copilot.repository.AnalysisRequestRepository;
import com.dhruv.incident_copilot.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AnalysisService {

    private static final List<AnalysisStatus> IN_FLIGHT_STATUSES =
            List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING);

    private final AnalysisRequestRepository analysisRequestRepository;
    private final IncidentRepository incidentRepository;
    private final AnalysisWorker analysisWorker;

    public AnalysisService(
            AnalysisRequestRepository analysisRequestRepository,
            IncidentRepository incidentRepository,
            AnalysisWorker analysisWorker) {
        this.analysisRequestRepository = analysisRequestRepository;
        this.incidentRepository = incidentRepository;
        this.analysisWorker = analysisWorker;
    }

    public AnalysisResponse submit(UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident not found: " + incidentId);
        }

        var existing = analysisRequestRepository.findFirstByIncidentIdAndStatusIn(incidentId, IN_FLIGHT_STATUSES);
        if (existing.isPresent()) {
            return AnalysisResponse.from(existing.get());
        }

        AnalysisRequest request = new AnalysisRequest();
        request.setIncidentId(incidentId);
        analysisRequestRepository.save(request);

        analysisWorker.process(request.getId());

        return AnalysisResponse.from(request);
    }

    public AnalysisResponse getById(UUID id) {
        return AnalysisResponse.from(analysisRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis request not found: " + id)));
    }

    public List<AnalysisResponse> getByIncidentId(UUID incidentId) {
        return analysisRequestRepository.findByIncidentIdOrderByRequestedAtDesc(incidentId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }
}
