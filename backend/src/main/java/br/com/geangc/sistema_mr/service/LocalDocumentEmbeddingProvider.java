package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalDocumentEmbeddingProvider implements DocumentEmbeddingProvider {

    private final RestClient client;
    private final DocumentProperties properties;

    private final ObjectMapper objectMapper;

    public LocalDocumentEmbeddingProvider(DocumentProperties properties, ObjectMapper objectMapper) {
        this.client = RestClient.builder().baseUrl(properties.embeddingServiceUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response;
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(
                    new EmbeddingRequest(texts, properties.embeddingModel())
            );
            response = client.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar a requisição de embeddings", exception);
        }
        if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
            throw new IllegalStateException("O serviço local de embeddings retornou uma quantidade inesperada de vetores");
        }
        return response.embeddings().stream().map(this::toArray).toList();
    }

    @Override
    public String model() {
        return properties.embeddingModel();
    }

    private float[] toArray(List<Float> values) {
        if (values == null || values.size() != properties.embeddingDimensions()) {
            throw new IllegalStateException("O serviço local de embeddings retornou uma dimensão inesperada");
        }
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private record EmbeddingRequest(List<String> texts, String model) {}

    private record EmbeddingResponse(String model, List<List<Float>> embeddings) {}
}
