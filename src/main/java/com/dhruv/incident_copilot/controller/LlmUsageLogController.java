package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.LlmUsageLogResponse;
import com.dhruv.incident_copilot.repository.LlmUsageLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/llm-usage")
public class LlmUsageLogController {

    private final LlmUsageLogRepository llmUsageLogRepository;

    public LlmUsageLogController(LlmUsageLogRepository llmUsageLogRepository) {
        this.llmUsageLogRepository = llmUsageLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LlmUsageLogResponse> getAll() {
        return llmUsageLogRepository.findAllByOrderByTimestampDesc().stream()
                .map(LlmUsageLogResponse::from)
                .toList();
    }
}
