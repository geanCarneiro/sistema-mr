package br.com.geangc.sistema_mr.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.runtime")
public record AgentRuntimeProperties(
        String defaultRoute,
        Limits limits,
        List<Route> routes
) {

    public AgentRuntimeProperties {
        limits = limits == null ? new Limits(8, 8, 8, 90, 200_000, 8_192) : limits;
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public record Limits(
            int maxSteps,
            int maxModelInvocations,
            int maxToolCalls,
            int maxDurationSeconds,
            int maxContextTokens,
            int maxOutputTokens
    ) {}

    public record Route(
            String id,
            String provider,
            String model,
            boolean enabled,
            int inputTokenLimit,
            int outputTokenLimit,
            Capabilities capabilities,
            Quota quota
    ) {
        public Route {
            capabilities = capabilities == null ? new Capabilities(false, false, false) : capabilities;
            quota = quota == null ? new Quota(null, null, null) : quota;
        }
    }

    public record Capabilities(
            boolean functionCalling,
            boolean structuredOutput,
            boolean thinking
    ) {}

    public record Quota(
            Integer requestsPerMinute,
            Integer inputTokensPerMinute,
            Integer requestsPerDay
    ) {}

    public Route defaultRouteConfig() {
        if (defaultRoute == null || defaultRoute.isBlank()) {
            throw new IllegalStateException("A rota padrão do AgentRuntime não foi configurada");
        }
        return routes.stream()
                .filter(route -> defaultRoute.equals(route.id()) && route.enabled())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "A rota padrão do AgentRuntime não está habilitada: " + defaultRoute));
    }

    public Route localRouteConfig() {
        return routes.stream()
                .filter(route -> route.enabled() && "local-ai".equals(route.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhuma rota local do AgentRuntime está habilitada"));
    }
}
