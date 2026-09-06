package br.com.geangc.sistema_mr.agent.gateway;

public enum ProviderSelectionPolicy {
    AUTO,
    PREFER_PROVIDER,
    REQUIRE_PROVIDER,
    LOCAL_FIRST,
    LOCAL_ONLY,
    CLOUD_ONLY
}
