package br.com.geangc.sistema_mr.memory.tool;

import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.memory.service.SemanticMemoryService;
import br.com.geangc.sistema_mr.state.model.Provenance;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SemanticMemoryToolConfig {
    private final SemanticMemoryService service;

    public SemanticMemoryToolConfig(SemanticMemoryService service) {
        this.service = service;
    }

    @Tool(name = "rememberSemanticFact", description = "Guarda um fato reutilizável; informe se foi declarado pelo usuário ou inferido, sua confiança, fonte e retenção.")
    public SemanticMemory rememberSemanticFact(RememberRequest request, ToolContext toolContext) {
        MemoryToolContext scope = MemoryToolContext.from(toolContext);
        return service.remember(scope.ownerSubject(), scope.contextId(), scope.subjectId(), request.key(), request.value(),
                request.origin(), request.confidence(), request.validUntil(), request.retentionPolicy(), provenance(request, scope));
    }

    @Tool(name = "searchSemanticMemory", description = "Consulta poucas memórias semânticas relevantes para a decisão atual.")
    public List<SemanticMemory> searchSemanticMemory(SearchRequest request, ToolContext toolContext) {
        MemoryToolContext scope = MemoryToolContext.from(toolContext);
        return service.findRelevant(scope.ownerSubject(), scope.contextId(), request.query(), request.limit());
    }

    @Tool(name = "updateSemanticMemory", description = "Substitui uma memória ativa por uma versão nova, preservando o vínculo de substituição e exigindo a versão observada.")
    public SemanticMemory updateSemanticMemory(UpdateRequest request, ToolContext toolContext) {
        MemoryToolContext scope = MemoryToolContext.from(toolContext);
        return service.update(UUID.fromString(request.memoryId()), scope.ownerSubject(), scope.contextId(), request.expectedVersion(),
                request.key(), request.value(), request.origin(), request.confidence(), request.validUntil(), request.retentionPolicy(), provenance(request, scope));
    }

    @Tool(name = "removeSemanticMemory", description = "Remove logicamente uma memória semântica do escopo atual, preservando sua proveniência histórica.")
    public String removeSemanticMemory(RemoveRequest request, ToolContext toolContext) {
        MemoryToolContext scope = MemoryToolContext.from(toolContext);
        service.delete(UUID.fromString(request.memoryId()), scope.ownerSubject(), scope.contextId(), request.expectedVersion());
        return "Memória removida: " + request.memoryId();
    }

    private static Provenance provenance(SourceRequest request, MemoryToolContext scope) {
        return new Provenance(
                blankOr(request.sourceType(), "AGENT_RUN"), blankOr(request.sourceId(), scope.runId().toString()),
                request.sourceVersion(), request.locator());
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public interface SourceRequest {
        String sourceType();
        String sourceId();
        String sourceVersion();
        String locator();
    }

    public record RememberRequest(String key, String value, String origin, double confidence, String validUntil,
                                  String retentionPolicy, String sourceType, String sourceId, String sourceVersion,
                                  String locator) implements SourceRequest {}

    public record SearchRequest(String query, int limit) {}

    public record UpdateRequest(String memoryId, int expectedVersion, String key, String value, String origin,
                                double confidence, String validUntil, String retentionPolicy, String sourceType,
                                String sourceId, String sourceVersion, String locator) implements SourceRequest {}

    public record RemoveRequest(String memoryId, int expectedVersion) {}
}
