package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.entity.LlmOutcome;
import com.dhruv.incident_copilot.entity.LlmUsageLog;
import com.dhruv.incident_copilot.repository.LlmUsageLogRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class ProtectedChatClient {

    private static final String INSTANCE_NAME = "llmCall";

    private final ChatClient chatClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final LlmUsageLogRepository llmUsageLogRepository;
    private final Executor llmCallExecutor;
    private final long timeoutSeconds;

    public ProtectedChatClient(
            ChatClient.Builder chatClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            LlmUsageLogRepository llmUsageLogRepository,
            @Qualifier("llmCallExecutor") Executor llmCallExecutor,
            @Value("${analysis.llm-call-timeout-seconds}") long timeoutSeconds) {
        this.chatClient = chatClientBuilder.build();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.llmUsageLogRepository = llmUsageLogRepository;
        this.llmCallExecutor = llmCallExecutor;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String call(String system, String user) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(INSTANCE_NAME);

        Supplier<String> rawCall = () -> chatClient.prompt().system(system).user(user).call().content();
        Supplier<String> protectedCall =
                CircuitBreaker.decorateSupplier(circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, rawCall));

        long start = System.currentTimeMillis();
        try {
            String result = CompletableFuture.supplyAsync(protectedCall, llmCallExecutor)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            logUsage(System.currentTimeMillis() - start, LlmOutcome.SUCCESS);
            return result;
        } catch (ExecutionException e) {
            long latency = System.currentTimeMillis() - start;
            Throwable cause = e.getCause();
            if (cause instanceof CallNotPermittedException) {
                throw unavailable(latency, LlmOutcome.CIRCUIT_OPEN, cause);
            }
            if (cause instanceof RequestNotPermitted) {
                throw unavailable(latency, LlmOutcome.RATE_LIMITED, cause);
            }
            throw unavailable(latency, LlmOutcome.ERROR, cause);
        } catch (java.util.concurrent.TimeoutException e) {
            throw unavailable(System.currentTimeMillis() - start, LlmOutcome.TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(System.currentTimeMillis() - start, LlmOutcome.ERROR, e);
        }
    }

    private LlmUnavailableException unavailable(long latencyMs, LlmOutcome outcome, Throwable cause) {
        logUsage(latencyMs, outcome);
        return new LlmUnavailableException(outcome, cause);
    }

    private void logUsage(long latencyMs, LlmOutcome outcome) {
        llmUsageLogRepository.save(new LlmUsageLog(latencyMs, outcome));
    }
}
