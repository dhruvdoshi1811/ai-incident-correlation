package com.dhruv.incident_copilot.repository;

import com.pgvector.PGvector;
import com.dhruv.incident_copilot.service.PostmortemMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PostmortemEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostmortemEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(UUID postmortemId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE postmortems SET embedding = ?::vector WHERE id = ?",
                new PGvector(embedding).getValue(), postmortemId);
    }

    public List<PostmortemMatch> findTopKSimilar(float[] queryEmbedding, int topK) {
        return jdbcTemplate.query(
                """
                SELECT id, title, content, (embedding <=> ?::vector) AS distance
                FROM postmortems
                WHERE embedding IS NOT NULL
                ORDER BY distance ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new PostmortemMatch(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                new PGvector(queryEmbedding).getValue(), topK);
    }
}
