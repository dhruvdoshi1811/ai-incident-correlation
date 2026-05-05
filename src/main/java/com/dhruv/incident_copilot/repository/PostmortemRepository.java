package com.dhruv.incident_copilot.repository;

import com.dhruv.incident_copilot.entity.Postmortem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostmortemRepository extends JpaRepository<Postmortem, UUID> {
}
