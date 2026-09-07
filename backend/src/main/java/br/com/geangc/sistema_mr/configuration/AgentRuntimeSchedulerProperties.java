package br.com.geangc.sistema_mr.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.runtime.scheduler")
public record AgentRuntimeSchedulerProperties(
        String storagePath,
        long retryDelayMillis
) {
    public AgentRuntimeSchedulerProperties {
        storagePath = storagePath == null || storagePath.isBlank()
                ? "./data/agent-runs" : storagePath;
        retryDelayMillis = retryDelayMillis <= 0 ? 1_000 : retryDelayMillis;
    }
}
