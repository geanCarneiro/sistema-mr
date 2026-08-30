package br.com.geangc.sistema_mr.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.document-vision")
public record DocumentVisionProperties(
        String model,
        double temperature,
        String thinkingLevel,
        int maxOutputTokens
) {}
