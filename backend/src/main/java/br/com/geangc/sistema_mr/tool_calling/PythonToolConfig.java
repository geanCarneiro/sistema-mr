package br.com.geangc.sistema_mr.tool_calling;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonToolConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonToolConfig.class);

    private final RestClient restClient;

    public record PythonRequest(String code) {}

    public record PythonResponse(
            String output,
            String error,
            Integer exitCode,
            boolean timedOut,
            boolean truncated
    ) {}

    public PythonToolConfig(@Value("${python.runner.url}") String runnerUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));

        this.restClient = RestClient.builder()
                .baseUrl(runnerUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Tool(description = "Executa Python 3 em um runner isolado para cálculos matemáticos, análise de dados e algoritmos determinísticos. Bibliotecas disponíveis: math, statistics, decimal, fractions, numpy, pandas, scipy e sympy.")
    public PythonResponse executePythonCode(PythonRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            return new PythonResponse("", "Código Python vazio", null, false, false);
        }

        try {
            PythonResponse response = restClient.post()
                    .uri("/execute")
                    .body(request)
                    .retrieve()
                    .body(PythonResponse.class);

            if (response == null) {
                return new PythonResponse("", "O runner Python não retornou resposta", null, false, false);
            }

            LOGGER.info(
                    "Execução Python concluída: exitCode={}, timedOut={}, truncated={}, outputLength={}, errorLength={}",
                    response.exitCode(),
                    response.timedOut(),
                    response.truncated(),
                    response.output() == null ? 0 : response.output().length(),
                    response.error() == null ? 0 : response.error().length()
            );
            return response;
        } catch (RestClientException exception) {
            LOGGER.error("Falha na comunicação com o runner Python", exception);
            return new PythonResponse(
                    "",
                    "O ambiente de cálculo Python está temporariamente indisponível",
                    null,
                    false,
                    false
            );
        }
    }
}
