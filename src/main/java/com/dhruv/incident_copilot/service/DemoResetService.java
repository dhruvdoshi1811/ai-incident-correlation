package com.dhruv.incident_copilot.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoResetService {

    private final JdbcTemplate jdbcTemplate;

    public DemoResetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void resetDemoData() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE outbound_notifications, analysis_requests, alerts, incidents, llm_usage_log CASCADE");
    }
}
