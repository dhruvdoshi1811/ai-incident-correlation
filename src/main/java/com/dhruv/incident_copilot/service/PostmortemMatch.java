package com.dhruv.incident_copilot.service;

import java.util.UUID;

public record PostmortemMatch(UUID id, String title, String content, double distance) {
}
