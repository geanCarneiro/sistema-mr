package br.com.geangc.sistema_mr.agent.service;

import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.model.QueuedChatRequest;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import br.com.geangc.sistema_mr.configuration.AgentRuntimeSchedulerProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fila local durável. O nome antigo é mantido para evitar uma quebra de API
 * desnecessária nos consumidores atuais do AgentRuntime.
 */
@Component
public class InMemoryRunScheduler {

    private static final Logger LOGGER = Logger.getLogger(InMemoryRunScheduler.class.getName());
    private static final String QUEUE_FILE = "pending-runs.txt";
    private final AgentRunRepository repository;
    private final Path storagePath;
    private final Path queueFile;
    private final BlockingQueue<UUID> queue = new LinkedBlockingQueue<>();
    private final Set<UUID> queued = new LinkedHashSet<>();

    @Autowired
    public InMemoryRunScheduler(
            AgentRunRepository repository,
            AgentRuntimeSchedulerProperties properties
    ) {
        this.repository = repository;
        this.storagePath = Path.of(properties.storagePath()).toAbsolutePath().normalize();
        this.queueFile = storagePath.resolve(QUEUE_FILE);
    }

    @PostConstruct
    void restorePendingRuns() {
        try {
            Files.createDirectories(storagePath);
            readQueueFile();
            repository.recoverInterruptedRuns();
            repository.findResumable().stream().map(AgentRun::id).forEach(this::offer);
            persistQueue();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível recuperar a fila local de execuções", exception);
        }
    }

    /** Persiste o pedido antes de o trabalho assíncrono começar. */
    public synchronized void register(QueuedChatRequest request) {
        ensureStorage();
        Properties values = new Properties();
        values.setProperty("runId", request.runId().toString());
        values.setProperty("prompt", request.prompt());
        values.setProperty("includeRelatedFiles", Boolean.toString(request.includeRelatedFiles()));
        if (request.subjectId() != null) {
            values.setProperty("subjectId", request.subjectId().toString());
        }
        if (request.privacyReviewFileId() != null) {
            values.setProperty("privacyReviewFileId", request.privacyReviewFileId().toString());
        }
        values.setProperty("attachmentIds", request.attachmentIds().stream()
                .map(UUID::toString).reduce((left, right) -> left + "," + right).orElse(""));
        writeAtomically(requestFile(request.runId()), values);
    }

    public synchronized void enqueue(UUID runId) {
        ensureStorage();
        offer(runId);
        persistQueue();
    }

    public UUID take() throws InterruptedException {
        UUID runId = queue.take();
        synchronized (this) {
            queued.remove(runId);
            persistQueue();
        }
        return runId;
    }

    public synchronized void complete(UUID runId) {
        queued.remove(runId);
        queue.remove(runId);
        try {
            Files.deleteIfExists(requestFile(runId));
            persistQueue();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível remover o snapshot da execução " + runId, exception);
        }
    }

    public Optional<QueuedChatRequest> load(UUID runId) {
        Path file = requestFile(runId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
            String storedRunId = values.getProperty("runId");
            if (!runId.toString().equals(storedRunId)) {
                LOGGER.warning("Snapshot de execução ignorado por runId inconsistente: " + file);
                return Optional.empty();
            }
            List<UUID> attachments = new ArrayList<>();
            String attachmentIds = values.getProperty("attachmentIds", "");
            if (!attachmentIds.isBlank()) {
                for (String value : attachmentIds.split(",")) {
                    attachments.add(UUID.fromString(value));
                }
            }
            String subjectId = values.getProperty("subjectId");
            String privacyReviewFileId = values.getProperty("privacyReviewFileId");
            return Optional.of(new QueuedChatRequest(
                    runId,
                    values.getProperty("prompt"),
                    attachments,
                    Boolean.parseBoolean(values.getProperty("includeRelatedFiles", "false")),
                    subjectId == null || subjectId.isBlank() ? null : UUID.fromString(subjectId),
                    privacyReviewFileId == null || privacyReviewFileId.isBlank()
                            ? null : UUID.fromString(privacyReviewFileId)
            ));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Não foi possível ler o snapshot da execução " + runId, exception);
        }
    }

    public int pendingCount() {
        return queue.size();
    }

    private synchronized void readQueueFile() throws IOException {
        if (!Files.isRegularFile(queueFile)) {
            return;
        }
        for (String value : Files.readAllLines(queueFile, StandardCharsets.UTF_8)) {
            if (!value.isBlank()) {
                try {
                    offer(UUID.fromString(value.trim()));
                } catch (IllegalArgumentException exception) {
                    LOGGER.warning("Entrada inválida removida da fila de execuções: " + value);
                }
            }
        }
    }

    private synchronized void offer(UUID runId) {
        if (queued.add(runId)) {
            queue.offer(runId);
        }
    }

    private void ensureStorage() {
        try {
            Files.createDirectories(storagePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível criar o armazenamento da fila", exception);
        }
    }

    private Path requestFile(UUID runId) {
        return storagePath.resolve(runId + ".properties");
    }

    private synchronized void persistQueue() {
        try {
            String content = queued.stream().map(UUID::toString).reduce("", (left, right) ->
                    left.isEmpty() ? right + System.lineSeparator() : left + right + System.lineSeparator());
            Path temporary = storagePath.resolve(QUEUE_FILE + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveAtomically(temporary, queueFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível persistir a fila de execuções", exception);
        }
    }

    private static void writeAtomically(Path target, Properties values) {
        try {
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Snapshot local da execução; não contém token de autenticação");
            }
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível persistir o snapshot da execução", exception);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
