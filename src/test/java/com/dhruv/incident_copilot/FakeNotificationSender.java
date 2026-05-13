package com.dhruv.incident_copilot;

import com.dhruv.incident_copilot.entity.OutboundNotification;
import com.dhruv.incident_copilot.service.NotificationSender;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeNotificationSender implements NotificationSender {

    private volatile boolean failing = false;
    private final List<OutboundNotification> sent = new CopyOnWriteArrayList<>();

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public List<OutboundNotification> getSent() {
        return Collections.unmodifiableList(sent);
    }

    @Override
    public void send(OutboundNotification notification) {
        if (failing) {
            throw new RuntimeException("Simulated webhook failure");
        }
        sent.add(notification);
    }
}
