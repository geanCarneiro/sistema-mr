package br.com.geangc.sistema_mr.storage;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(DocumentProperties properties) {
        this.root = properties.storageRoot().toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    public StoredOriginal storeOriginal(UUID id, InputStream inputStream) throws IOException {
        Path directory = fileDirectory(id);
        Files.createDirectories(directory);
        String key = id + "/original";
        Path target = resolve(key);
        MessageDigest digest = sha256();
        try (DigestInputStream source = new DigestInputStream(inputStream, digest)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredOriginal(key, target, HexFormat.of().formatHex(digest.digest()));
    }

    public String writeContext(UUID id, String context) throws IOException {
        Files.createDirectories(fileDirectory(id));
        String key = id + "/context.md";
        Files.writeString(resolve(key), context, StandardCharsets.UTF_8);
        return key;
    }

    public String readText(String key) throws IOException {
        return Files.readString(resolve(key), StandardCharsets.UTF_8);
    }

    public Path path(String key) {
        return resolve(key);
    }

    public Resource resource(String key) throws IOException {
        Resource resource = new UrlResource(resolve(key).toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Arquivo armazenado não está disponível");
        }
        return resource;
    }

    public void delete(UUID id) throws IOException {
        Path directory = fileDirectory(id);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Path fileDirectory(UUID id) {
        Path directory = root.resolve(id.toString()).normalize();
        ensureWithinRoot(directory);
        return directory;
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        ensureWithinRoot(resolved);
        return resolved;
    }

    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    public record StoredOriginal(String storageKey, Path path, String sha256) {}
}
