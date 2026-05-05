package com.dhruv.incident_copilot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAiConfig {

    @Bean
    @Primary
    public FakeEmbeddingModel embeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    @Primary
    public FakeChatModel chatModel() {
        return new FakeChatModel();
    }
}
