package com.dhruv.incident_copilot.dto;

import com.dhruv.incident_copilot.entity.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {
}
