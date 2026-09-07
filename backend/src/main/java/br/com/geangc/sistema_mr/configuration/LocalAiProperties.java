package br.com.geangc.sistema_mr.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.local")
public record LocalAiProperties(
        String serviceUrl,
        String model,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int decisionMaxTokens,
        int visionMaxTokens
) {
    public LocalAiProperties {
        serviceUrl = serviceUrl == null || serviceUrl.isBlank() ? "http://127.0.0.1:8083" : serviceUrl;
        model = model == null || model.isBlank() ? "gemma-3-4b-it-q4" : model;
        connectTimeoutSeconds = connectTimeoutSeconds < 1 ? 2 : connectTimeoutSeconds;
        readTimeoutSeconds = readTimeoutSeconds < 1 ? 90 : readTimeoutSeconds;
        decisionMaxTokens = decisionMaxTokens < 1 ? 256 : decisionMaxTokens;
        visionMaxTokens = visionMaxTokens < 1 ? 2048 : visionMaxTokens;
    }
}
