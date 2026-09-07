package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LocalDocumentEmbeddingProvider implements DocumentEmbeddingProvider {

    private final RestClient client;
    private final DocumentProperties properties;

    public LocalDocumentEmbeddingProvider(DocumentProperties properties) {
        this.client = RestClient.builder().baseUrl(properties.embeddingServiceUrl()).build();
        this.properties = properties;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response = client.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(texts, properties.embeddingModel()))
                .retrieve()
                .body(EmbeddingResponse.class);
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
