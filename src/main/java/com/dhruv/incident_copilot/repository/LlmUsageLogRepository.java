package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.LlmUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLog, UUID> {

    List<LlmUsageLog> findAllByOrderByTimestampDesc();
}
