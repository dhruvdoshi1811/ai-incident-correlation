package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.OutboundNotification;

public interface NotificationSender {

    void send(OutboundNotification notification);
}
