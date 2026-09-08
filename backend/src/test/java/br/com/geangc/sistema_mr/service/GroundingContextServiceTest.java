package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentSensitivity;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.repository.DocumentRepository.GroundingChunkMatch;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroundingContextServiceTest {

    @Test
    void includesOnlyExplicitAttachmentsWhenRelatedSearchIsDisabled() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = readyFile(id, "relatório.pdf", "explicit-context", 20);

        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of(file));
        when(storage.readText("explicit-context")).thenReturn("CONTEÚDO COMPLETO DO ARQUIVO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Resuma", List.of(id), false);

        assertTrue(prepared.modelPrompt().contains("CONTEÚDO COMPLETO DO ARQUIVO"));
        assertTrue(prepared.modelPrompt().contains("Resuma"));
        assertEquals(1, prepared.files().size());
        assertTrue(prepared.files().getFirst().explicitlyAttached());
        verify(repository, never()).hasReadyFiles("chat-owner", "owner");
        verifyNoInteractions(embeddings);
    }

    @Test
    void combinesExplicitAndSemanticAttachmentsWhenRelatedSearchIsEnabled() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID explicitId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        ChatFile explicit = readyFile(explicitId, "contrato.pdf", "explicit-context", 20);
        ChatFile related = readyFile(relatedId, "aditivo.pdf", "related-context", 20);
        List<Float> embedding = List.of(.1f, .2f);

        when(repository.findReadyOwnedByIds(List.of(explicitId), "chat-owner", "owner"))
                .thenReturn(List.of(explicit));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Compare")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding))
                .thenReturn(List.of(new GroundingChunkMatch(related, UUID.randomUUID(), 0, "ADITIVO", .91)));
        when(storage.readText("explicit-context")).thenReturn("CONTRATO");
        when(storage.readText("related-context")).thenReturn("ADITIVO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Compare", List.of(explicitId), true);

        assertEquals(2, prepared.files().size());
        assertTrue(prepared.files().get(0).explicitlyAttached());
        assertFalse(prepared.files().get(1).explicitlyAttached());
        assertTrue(prepared.modelPrompt().contains("CONTRATO"));
        assertTrue(prepared.modelPrompt().contains("ADITIVO"));
    }

    @Test
    void usesSemanticSearchWhenNoAttachmentsAreSelected() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID relatedId = UUID.randomUUID();
        ChatFile related = readyFile(relatedId, "manual.pdf", "related-context", 20);
        List<Float> embedding = List.of(.3f, .4f);

        when(repository.findReadyOwnedByIds(List.of(), "chat-owner", "owner")).thenReturn(List.of());
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Como configurar?")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding))
                .thenReturn(List.of(new GroundingChunkMatch(related, UUID.randomUUID(), 0, "MANUAL", .84)));
        when(storage.readText("related-context")).thenReturn("MANUAL");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Como configurar?", List.of(), false);

        assertEquals(1, prepared.files().size());
        assertFalse(prepared.files().getFirst().explicitlyAttached());
        assertTrue(prepared.modelPrompt().contains("MANUAL"));
    }

    @Test
    void rejectsAttachmentsThatAreMissingNotOwnedOrNotReady() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of());

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.prepare("chat-owner", "owner", "Resuma", List.of(id), false));

        assertTrue(exception.getMessage().contains("não existem"));
        verifyNoInteractions(storage, embeddings);
    }

    @Test
    void rejectsExplicitAttachmentsThatExceedTheContextBudget() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = readyFile(id, "relatório.pdf", "context-key", 101);
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of(file));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Pergunta")).thenReturn(List.of(.1f, .2f));
        when(embeddings.estimateTokens("evidência")).thenReturn(2);
        when(repository.searchReadyChunks("chat-owner", "owner", List.of(.1f, .2f)))
                .thenReturn(List.of(new GroundingChunkMatch(file, UUID.randomUUID(), 0, "evidência", .9)));

        var service = new GroundingContextService(
                repository, mock(DocumentStorage.class), embeddings, properties(100)
        );

        assertThrows(GroundingContextLimitException.class,
                () -> service.prepare("chat-owner", "owner", "Pergunta", List.of(id), false));
    }

    @Test
    void stopsWithNoEvidenceWhenExplicitAttachmentHasNoRelevantChunks() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = readyFile(id, "manual.md", "context-key", 20_000);
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of(file));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Pergunta sem resposta")).thenReturn(List.of(.1f, .2f));
        when(repository.searchReadyChunks("chat-owner", "owner", List.of(.1f, .2f))).thenReturn(List.of());

        var service = new GroundingContextService(
                repository, mock(DocumentStorage.class), embeddings, properties(200_000)
        );

        GroundingEvidenceInsufficientException exception = assertThrows(
                GroundingEvidenceInsufficientException.class,
                () -> service.prepare("chat-owner", "owner", "Pergunta sem resposta", List.of(id), false));

        assertEquals("O anexo selecionado não retornou evidências relevantes para a solicitação.", exception.getMessage());
    }

    @Test
    void recordsEvidenceSufficientAsTheProgressiveRetrievalStopReason() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID fileId = UUID.randomUUID();
        ChatFile large = readyFile(fileId, "manual-grande.md", "large-context", 20_000);
        List<Float> embedding = List.of(.7f, .8f);
        GroundingChunkMatch anchor = new GroundingChunkMatch(large, UUID.randomUUID(), 12, "TRECHO", .95);

        when(repository.findReadyOwnedByIds(List.of(fileId), "chat-owner", "owner")).thenReturn(List.of(large));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Localize o trecho")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding)).thenReturn(List.of(anchor));

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Localize o trecho", List.of(fileId), false);

        assertEquals(GroundingContextService.RetrievalStopReason.EVIDENCE_SUFFICIENT,
                prepared.retrievalStopReason());
    }

    @Test
    void recordsNoEvidenceFoundWhenAutomaticSearchReturnsNoChunks() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        List<Float> embedding = List.of(.1f, .2f);
        when(repository.findReadyOwnedByIds(List.of(), "chat-owner", "owner")).thenReturn(List.of());
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Pergunta sem evidência")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding)).thenReturn(List.of());

        var service = new GroundingContextService(
                repository, mock(DocumentStorage.class), embeddings, properties(200_000)
        );
        var prepared = service.prepare(
                "chat-owner", "owner", "Pergunta sem evidência", List.of(), false);

        assertEquals(GroundingContextService.RetrievalStopReason.NO_EVIDENCE_FOUND,
                prepared.retrievalStopReason());
        assertTrue(prepared.evidences().isEmpty());
    }

    @Test
    void usesRelevantChunksAndNeighborsForLargeRelatedDocuments() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID fileId = UUID.randomUUID();
        ChatFile large = readyFile(fileId, "manual-grande.pdf", "large-context", 20_000);
        List<Float> embedding = List.of(.5f, .6f);
        GroundingChunkMatch anchor = new GroundingChunkMatch(large, fileId, 40, "TRECHO CENTRAL", .93);
        GroundingChunkMatch previous = new GroundingChunkMatch(large, UUID.randomUUID(), 39, "CABEÇALHO DA SEÇÃO", .93);
        GroundingChunkMatch next = new GroundingChunkMatch(large, UUID.randomUUID(), 41, "EXCEÇÃO DA SEÇÃO", .93);

        when(repository.findReadyOwnedByIds(List.of(), "chat-owner", "owner")).thenReturn(List.of());
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Qual é a exceção?")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding)).thenReturn(List.of(anchor));
        when(repository.findChunksAround(eq("chat-owner"), eq("owner"), anyList(), eq(1)))
                .thenReturn(List.of(previous, anchor, next));

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Qual é a exceção?", List.of(), false);

        assertEquals(1, prepared.files().size());
        assertEquals(3, prepared.evidences().size());
        assertTrue(prepared.modelPrompt().contains("CABEÇALHO DA SEÇÃO"));
        assertTrue(prepared.modelPrompt().contains("EXCEÇÃO DA SEÇÃO"));
        verify(storage, never()).readText("large-context");
    }

    @Test
    void usesRelevantChunksAndNeighborsForLargeExplicitDocuments() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID fileId = UUID.randomUUID();
        ChatFile large = readyFile(fileId, "manual-grande.md", "large-context", 20_000);
        List<Float> embedding = List.of(.7f, .8f);
        GroundingChunkMatch anchor = new GroundingChunkMatch(large, UUID.randomUUID(), 12, "TRECHO EXPLÍCITO", .95);
        GroundingChunkMatch previous = new GroundingChunkMatch(large, UUID.randomUUID(), 11, "CONTEXTO ANTERIOR", .95);
        GroundingChunkMatch next = new GroundingChunkMatch(large, UUID.randomUUID(), 13, "CONTEXTO POSTERIOR", .95);

        when(repository.findReadyOwnedByIds(List.of(fileId), "chat-owner", "owner"))
                .thenReturn(List.of(large));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Localize o trecho")).thenReturn(embedding);
        when(repository.searchReadyChunks("chat-owner", "owner", embedding)).thenReturn(List.of(anchor));
        when(repository.findChunksAround(eq("chat-owner"), eq("owner"), anyList(), eq(1)))
                .thenReturn(List.of(previous, anchor, next));

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Localize o trecho", List.of(fileId), false);

        assertEquals(1, prepared.files().size());
        assertTrue(prepared.files().getFirst().explicitlyAttached());
        assertEquals(3, prepared.evidences().size());
        assertTrue(prepared.modelPrompt().contains("TRECHO EXPLÍCITO"));
        assertTrue(prepared.modelPrompt().contains("CONTEXTO ANTERIOR"));
        assertTrue(prepared.modelPrompt().contains("CONTEXTO POSTERIOR"));
        verify(storage, never()).readText("large-context");
    }

    @Test
    void usesCompleteRepresentationOnlyForLocalOnlyDocuments() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ChatFile sensitive = new ChatFile(
                id, "chat-owner", "owner", "dados.pdf", "application/pdf", 10, "hash",
                id + "/original", "anonymized-context", id + "/full-context", id + "/mapping",
                DocumentStatus.READY, null, 20, "multilingual-e5-small", now, now,
                DocumentSensitivity.SENSITIVE
        );
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner"))
                .thenReturn(List.of(sensitive));
        when(storage.readText(id + "/full-context")).thenReturn("DADO COMPLETO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Resuma", List.of(id), false);

        assertEquals(br.com.geangc.sistema_mr.privacy.PrivacyMode.LOCAL_ONLY,
                prepared.privacyDecision().mode());
        assertTrue(prepared.modelPrompt().contains("DADO COMPLETO"));
        verify(storage, never()).readText("anonymized-context");
    }

    private static ChatFile readyFile(UUID id, String name, String contextStorageKey, int tokenCount) {
        Instant now = Instant.now();
        return new ChatFile(
                id, "chat-owner", "owner", name, "application/pdf", 10, "hash",
                id + "/original", contextStorageKey, DocumentStatus.READY, null, tokenCount,
                "gemini-embedding-2", now, now
        );
    }

    private static DocumentProperties properties(int budget) {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, budget,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
