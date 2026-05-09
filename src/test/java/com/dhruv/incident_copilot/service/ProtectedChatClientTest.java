package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.FakeChatModel;
import com.dhruv.incident_copilot.entity.LlmOutcome;
import com.dhruv.incident_copilot.entity.LlmUsageLog;
import com.dhruv.incident_copilot.repository.LlmUsageLogRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProtectedChatClientTest {

    private FakeChatModel fakeChatModel;
    private LlmUsageLogRepository llmUsageLogRepository;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void setUp() {
        fakeChatModel = new FakeChatModel();
        llmUsageLogRepository = mock(LlmUsageLogRepository.class);
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
    }

    private ProtectedChatClient client(long timeoutSeconds) {
        return new ProtectedChatClient(
                ChatClient.builder(fakeChatModel),
                circuitBreakerRegistry,
                rateLimiterRegistry,
                llmUsageLogRepository,
                Executors.newCachedThreadPool(),
                timeoutSeconds);
    }

    @Test
    void successfulCallReturnsContentAndLogsSuccess() {
        fakeChatModel.setCannedResponse("hello from fake model");

        String result = client(5).call("system", "user");

        assertThat(result).isEqualTo("hello from fake model");
        ArgumentCaptor<LlmUsageLog> captor = ArgumentCaptor.forClass(LlmUsageLog.class);
        verify(llmUsageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(LlmOutcome.SUCCESS);
    }

    @Test
    void slowCallExceedingTimeoutThrowsAndLogsTimeout() {
        fakeChatModel.setDelayMillis(3000);

        assertThatThrownBy(() -> client(1).call("system", "user"))
                .isInstanceOf(LlmUnavailableException.class)
                .satisfies(e -> assertThat(((LlmUnavailableException) e).getOutcome()).isEqualTo(LlmOutcome.TIMEOUT));

        ArgumentCaptor<LlmUsageLog> captor = ArgumentCaptor.forClass(LlmUsageLog.class);
        verify(llmUsageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(LlmOutcome.TIMEOUT);
    }
}
