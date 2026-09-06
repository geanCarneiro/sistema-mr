package br.com.geangc.sistema_mr.agent.gateway;

public interface ModelGateway {

    ModelResponse invoke(ModelRequest request);
}
