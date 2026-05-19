package com.dhruv.incident_copilot.config;

import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.repository.PostmortemRepository;
import com.dhruv.incident_copilot.service.IncidentScenario;
import com.dhruv.incident_copilot.service.PostmortemService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ScenarioPostmortemSeeder implements CommandLineRunner {

    private final PostmortemRepository postmortemRepository;
    private final PostmortemService postmortemService;

    public ScenarioPostmortemSeeder(PostmortemRepository postmortemRepository, PostmortemService postmortemService) {
        this.postmortemRepository = postmortemRepository;
        this.postmortemService = postmortemService;
    }

    @Override
    public void run(String... args) {
        for (IncidentScenario scenario : IncidentScenario.values()) {
            if (postmortemRepository.findByTitle(scenario.postmortemTitle()).isPresent()) {
                continue;
            }
            postmortemService.create(new PostmortemRequest(scenario.postmortemTitle(), scenario.postmortemContent()));
        }
    }
}
