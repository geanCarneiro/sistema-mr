package br.com.geangc.sistema_mr.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesOriginalAndContextSeparatelyAndDeletesOnlyItsDirectory() throws Exception {
        DocumentStorage storage = new DocumentStorage(properties(temporaryDirectory));
        storage.initialize();
        UUID id = UUID.randomUUID();

        var original = storage.storeOriginal(
                id,
                new ByteArrayInputStream("conteúdo original".getBytes(StandardCharsets.UTF_8))
        );
        String contextKey = storage.writeContext(id, "# versão textual");

        assertTrue(Files.exists(original.path()));
        assertEquals("# versão textual", storage.readText(contextKey));
        assertEquals(64, original.sha256().length());

        storage.delete(id);
        assertFalse(Files.exists(temporaryDirectory.resolve(id.toString())));
    }

    @Test
    void rejectsKeysOutsideConfiguredRoot() throws Exception {
        DocumentStorage storage = new DocumentStorage(properties(temporaryDirectory));
        storage.initialize();
        assertThrows(IllegalArgumentException.class, () -> storage.path("../outside"));
    }

    private static DocumentProperties properties(Path root) {
        return new DocumentProperties(
                root, 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr(true, "por+eng", 120)
        );
    }
}
