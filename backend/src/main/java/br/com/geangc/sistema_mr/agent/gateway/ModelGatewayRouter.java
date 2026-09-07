package br.com.geangc.sistema_mr.agent.gateway;

import br.com.geangc.sistema_mr.configuration.AgentRuntimeProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ModelGatewayRouter implements ModelGateway {

    private final GeminiModelGateway gemini;
    private final LocalModelGateway local;
    private final AgentRuntimeProperties properties;

    public ModelGatewayRouter(
            GeminiModelGateway gemini,
            LocalModelGateway local,
            AgentRuntimeProperties properties
    ) {
        this.gemini = gemini;
        this.local = local;
        this.properties = properties;
    }

    @Override
    public ModelResponse invoke(ModelRequest request) {
        AgentRuntimeProperties.Route route = properties.routes().stream()
                .filter(candidate -> candidate.id().equals(request.routeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rota não encontrada: " + request.routeId()));
        return "local-ai".equals(route.provider()) ? local.invoke(request) : gemini.invoke(request);
    }
}
