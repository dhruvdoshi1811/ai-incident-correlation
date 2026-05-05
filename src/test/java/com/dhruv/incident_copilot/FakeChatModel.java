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

    public String getLastPromptText() {
        return lastPromptText;
    }

    public void setCannedResponse(String cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        this.lastPromptText = prompt.getContents();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(cannedResponse))));
    }
}
