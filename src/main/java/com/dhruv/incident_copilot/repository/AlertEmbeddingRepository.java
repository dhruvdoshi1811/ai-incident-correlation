package com.dhruv.incident_copilot.repository;

import com.pgvector.PGvector;
import com.dhruv.incident_copilot.service.CorrelationCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AlertEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(UUID alertId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE alerts SET embedding = ?::vector WHERE id = ?",
                new PGvector(embedding).getValue(), alertId);
    }

    public Optional<CorrelationCandidate> findNearestActiveIncident(UUID excludeAlertId, float[] queryEmbedding) {
        var results = jdbcTemplate.query(
                """
                SELECT a.incident_id AS incident_id, (a.embedding <=> ?::vector) AS distance
                FROM alerts a
                JOIN incidents i ON a.incident_id = i.id
                WHERE i.status IN ('OPEN', 'INVESTIGATING')
                  AND a.id <> ?
                  AND a.embedding IS NOT NULL
                ORDER BY distance ASC
                LIMIT 1
                """,
                (rs, rowNum) -> new CorrelationCandidate(
                        UUID.fromString(rs.getString("incident_id")), rs.getDouble("distance")),
                new PGvector(queryEmbedding).getValue(), excludeAlertId);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
