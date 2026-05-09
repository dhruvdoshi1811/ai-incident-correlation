package com.dhruv.incident_copilot;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

public class FakeChatModel implements ChatModel {

    private volatile String lastPromptText;
    private volatile String cannedResponse = "Fake root cause analysis response.";
    private volatile boolean failing = false;
    private volatile long delayMillis = 0;

    public String getLastPromptText() {
        return lastPromptText;
    }

    public void setCannedResponse(String cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        this.lastPromptText = prompt.getContents();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failing) {
            throw new RuntimeException("Simulated LLM failure");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(cannedResponse))));
    }
}
