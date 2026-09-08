package br.com.geangc.sistema_mr.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class DocumentGroundingToolConfig {

    private final GroundingContextService groundingContextService;

    public DocumentGroundingToolConfig(GroundingContextService groundingContextService) {
        this.groundingContextService = groundingContextService;
    }

    @Tool(name = "searchDocumentEvidence", description = "Busca poucos trechos documentais relevantes para uma pergunta. Retorna evidências anonimizadas com arquivo, posição e similaridade.")
    public List<GroundingContextService.GroundingEvidence> searchDocumentEvidence(
            SearchRequest request,
            ToolContext toolContext
    ) {
        Scope scope = Scope.from(toolContext);
        int limit = request == null || request.limit() == null ? 4 : request.limit();
        return groundingContextService.searchEvidence(scope.conversationId(), scope.ownerSubject(),
                request == null ? null : request.query(), Math.max(1, Math.min(limit, 8)));
    }

    @Tool(name = "readDocumentSection", description = "Lê uma seção e seus chunks vizinhos de um documento autorizado no contexto atual. Use o arquivo e a posição retornados pela busca.")
    public List<GroundingContextService.GroundingEvidence> readDocumentSection(
            SectionRequest request,
            ToolContext toolContext
    ) {
        Scope scope = Scope.from(toolContext);
        if (request == null || request.fileId() == null || request.position() == null) {
            throw new IllegalArgumentException("Arquivo e posição da seção são obrigatórios");
        }
        int radius = request.radius() == null ? 1 : request.radius();
        return groundingContextService.readDocumentSection(scope.conversationId(), scope.ownerSubject(),
                UUID.fromString(request.fileId()), request.position(), Math.max(0, Math.min(radius, 3)));
    }

    @Tool(name = "readFullDocument", description = "Lê o documento completo somente quando a execução estiver em LOCAL_ONLY e a evidência parcial não for suficiente.")
    public String readFullDocument(FullDocumentRequest request, ToolContext toolContext) {
        Scope scope = Scope.from(toolContext);
        if (request == null || request.fileId() == null) {
            throw new IllegalArgumentException("O arquivo é obrigatório");
        }
        return groundingContextService.readFullDocument(scope.conversationId(), scope.ownerSubject(),
                UUID.fromString(request.fileId()), scope.privacyMode());
    }

    public record SearchRequest(String query, Integer limit) {}

    public record SectionRequest(String fileId, Integer position, Integer radius) {}

    public record FullDocumentRequest(String fileId) {}

    private record Scope(String conversationId, String ownerSubject, String privacyMode) {
        private static Scope from(ToolContext context) {
            if (context == null || context.getContext() == null) {
                throw new IllegalArgumentException("O contexto da ferramenta documental é obrigatório");
            }
            Map<String, Object> values = context.getContext();
            return new Scope(required(values, "conversationId"), required(values, "ownerSubject"),
                    required(values, "privacyMode"));
        }

        private static String required(Map<String, Object> values, String key) {
            Object value = values.get(key);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("O contexto da ferramenta não contém " + key);
            }
            return value.toString();
        }
    }
}
