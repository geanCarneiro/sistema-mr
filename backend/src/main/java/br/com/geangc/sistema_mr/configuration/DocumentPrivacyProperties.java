package br.com.geangc.sistema_mr.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.documents.privacy")
public record DocumentPrivacyProperties(String mappingEncryptionKey) {}
