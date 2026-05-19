package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.dto.AlertStormResult;
import com.dhruv.incident_copilot.entity.Alert;
import com.dhruv.incident_copilot.repository.AlertRepository;
import com.dhruv.incident_copilot.service.DemoService;
import com.dhruv.incident_copilot.service.IncidentScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DemoServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DemoService demoService;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void simulateAlertStormFiresAllAlertsCyclingThroughScenarioTemplates() {
        IncidentScenario scenario = IncidentScenario.DB_CONNECTION_POOL_EXHAUSTION;

        AlertStormResult result = demoService.simulateAlertStorm(scenario, 9);

        assertThat(result.alertsFired()).isEqualTo(9);

        List<Alert> firedAlerts = result.resultingIncidentIds().stream()
                .flatMap(id -> alertRepository.findByIncidentId(id).stream())
                .toList();
        assertThat(firedAlerts).hasSize(9);

        List<IncidentScenario.AlertTemplate> templates = scenario.alertTemplates();
        assertThat(firedAlerts)
                .extracting(Alert::getSourceSystem, a -> a.getSeverity().name(), Alert::getTitle)
                .allMatch(fields -> templates.stream().anyMatch(t ->
                        fields.equals(tuple(t.sourceSystem(), t.severity().name(), t.title()))));

        for (IncidentScenario.AlertTemplate template : templates) {
            assertThat(firedAlerts)
                    .anyMatch(a -> a.getTitle().equals(template.title()));
        }
    }
}
