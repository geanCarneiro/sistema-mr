package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaddleOcrClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaddleOcrClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaddleOcrClient(DocumentProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.ocr().timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.ocr().serviceUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public Health health() {
        try {
            Health response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Health.class);
            if (response == null || !response.ready() || !"UP".equals(response.status())) {
                throw new OcrInfrastructureException("O serviço PaddleOCR respondeu sem confirmar que está pronto");
            }
            return response;
        } catch (OcrInfrastructureException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new OcrInfrastructureException(
                    "O serviço PaddleOCR está indisponível em sua URL configurada", exception
            );
        }
    }

    public OcrResult extract(Path path, String originalName, String mimeType) throws IOException {
        Health health = health();
        byte[] content = Files.readAllBytes(path);
        byte[] requestBody = objectMapper.writeValueAsBytes(new OcrRequest(
                originalName,
                mimeType,
                Base64.getEncoder().encodeToString(content)
        ));

        try {
            OcrResult response = restClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve()
                    .body(OcrResult.class);
            if (response == null) {
                throw new OcrProcessingException("O PaddleOCR não retornou um resultado");
            }
            LOGGER.info(
                    "OCR local concluído: file={}, model={}, lines={}, meanConfidence={}, durationMs={}",
                    originalName,
                    response.model() == null ? health.model() : response.model(),
                    response.lines() == null ? 0 : response.lines().size(),
                    response.meanConfidence(),
                    response.durationMs()
            );
            return response;
        } catch (OcrProcessingException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new OcrProcessingException("O PaddleOCR falhou ao processar " + originalName, exception);
        }
    }

    public record Health(String status, boolean ready, String model, String engine) {}

    private record OcrRequest(String originalName, String mimeType, String contentBase64) {}

    public record OcrResult(
            String model,
            List<OcrLine> lines,
            double meanConfidence,
            long durationMs
    ) {
        public OcrResult {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record OcrLine(int page, String text, double confidence, List<List<Integer>> box) {}
}
