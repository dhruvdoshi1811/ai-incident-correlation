package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.OutboundNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WebhookNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final String webhookUrl;

    public WebhookNotificationSender(@Value("${notification.webhook-url}") String webhookUrl) {
        this.restClient = RestClient.create();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(OutboundNotification notification) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification.getPayload())
                .retrieve()
                .toBodilessEntity();
    }
}
