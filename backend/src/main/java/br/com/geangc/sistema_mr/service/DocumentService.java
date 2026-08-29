package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final List<String> BLOCKED_MIME_PREFIXES = List.of(
            "application/zip", "application/x-rar", "application/x-7z", "application/x-tar",
            "application/x-executable", "application/x-msdownload", "application/java-archive"
    );

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final DocumentIngestionService ingestionService;
    private final DocumentProperties properties;
    private final Tika tika = new Tika();

    public DocumentService(
            DocumentRepository repository,
            DocumentStorage storage,
            DocumentIngestionService ingestionService,
            DocumentProperties properties
    ) {
        this.repository = repository;
        this.storage = storage;
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    public List<ChatFile> upload(
            List<MultipartFile> files,
            String conversationId,
            String ownerSubject
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Selecione pelo menos um arquivo");
        }
        if (files.size() > properties.maxFilesPerUpload()) {
            throw new IllegalArgumentException("O limite é de " + properties.maxFilesPerUpload() + " arquivos por envio");
        }
        files.forEach(this::validate);
        List<ChatFile> created = new ArrayList<>(files.size());
        try {
            for (MultipartFile file : files) {
                created.add(store(file, conversationId, ownerSubject));
            }
        } catch (RuntimeException exception) {
            created.forEach(file -> rollback(file, conversationId, ownerSubject));
            throw exception;
        }
        created.forEach(file -> ingestionService.process(file.id()));
        return List.copyOf(created);
    }

    public List<ChatFile> list(String conversationId, String ownerSubject) {
        return repository.listOwned(conversationId, ownerSubject);
    }

    public Download download(UUID id, String conversationId, String ownerSubject) {
        ChatFile file = owned(id, conversationId, ownerSubject);
        try {
            return new Download(file, storage.resource(file.originalStorageKey()));
        } catch (IOException exception) {
            throw new IllegalStateException("O conteúdo original não está disponível", exception);
        }
    }

    public void delete(UUID id, String conversationId, String ownerSubject) {
        owned(id, conversationId, ownerSubject);
        repository.deleteOwned(id, conversationId, ownerSubject);
        try {
            storage.delete(id);
        } catch (IOException exception) {
            throw new IllegalStateException("Os metadados foram removidos, mas o arquivo físico não pôde ser excluído", exception);
        }
    }

    private ChatFile store(MultipartFile upload, String conversationId, String ownerSubject) {
        validate(upload);
        UUID id = UUID.randomUUID();
        String originalName = safeName(upload.getOriginalFilename());
        try {
            var stored = storage.storeOriginal(id, upload.getInputStream());
            String mimeType;
            try (var input = java.nio.file.Files.newInputStream(stored.path())) {
                mimeType = tika.detect(input, originalName);
            }
            validateMimeType(mimeType);
            Instant now = Instant.now();
            ChatFile file = new ChatFile(
                    id, conversationId, ownerSubject, originalName, mimeType, upload.getSize(),
                    stored.sha256(), stored.storageKey(), null, DocumentStatus.QUEUED, null, 0,
                    properties.embeddingModel(), now, now
            );
            return repository.create(file);
        } catch (Exception exception) {
            try {
                storage.delete(id);
            } catch (IOException ignored) {
                // O erro original é mais útil para o cliente.
            }
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("Não foi possível armazenar " + originalName, exception);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivos vazios não são aceitos");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new IllegalArgumentException("Cada arquivo pode ter no máximo "
                    + properties.maxFileSizeBytes() / (1024 * 1024) + " MB");
        }
    }

    private static void validateMimeType(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "application/octet-stream".equals(normalized)
                || BLOCKED_MIME_PREFIXES.stream().anyMatch(normalized::startsWith)) {
            throw new IllegalArgumentException("Tipo de arquivo não suportado: " + mimeType);
        }
    }

    private static String safeName(String name) {
        String cleaned = StringUtils.cleanPath(name == null ? "arquivo" : name);
        String leaf = java.nio.file.Path.of(cleaned).getFileName().toString();
        if (leaf.isBlank() || leaf.contains("..")) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }
        return leaf.length() <= 255 ? leaf : leaf.substring(0, 255);
    }

    private ChatFile owned(UUID id, String conversationId, String ownerSubject) {
        return repository.findOwned(id, conversationId, ownerSubject).orElseThrow(DocumentNotFoundException::new);
    }

    private void rollback(ChatFile file, String conversationId, String ownerSubject) {
        try {
            repository.deleteOwned(file.id(), conversationId, ownerSubject);
            storage.delete(file.id());
        } catch (Exception ignored) {
            // Mantém o erro que causou o rollback como resposta principal.
        }
    }

    public record Download(ChatFile file, Resource resource) {}
}
