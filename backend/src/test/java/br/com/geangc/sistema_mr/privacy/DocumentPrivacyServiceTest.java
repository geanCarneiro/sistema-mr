package br.com.geangc.sistema_mr.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.geangc.sistema_mr.configuration.DocumentPrivacyProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DocumentPrivacyServiceTest {

    @Test
    void preparesAndResolvesAFileScopedMapping() {
        DocumentPrivacyService service = new DocumentPrivacyService(
                new DocumentAnonymizer(),
                new DocumentMappingJson(new ObjectMapper()),
                new DocumentMappingCrypto(new DocumentPrivacyProperties("mvp-key"))
        );
        UUID fileId = UUID.randomUUID();

        var prepared = service.prepare(fileId, "Nome: Maria Oliveira; CPF: 987.654.321-00");

        assertFalse(prepared.anonymizedText().contains("Maria Oliveira"));
        assertEquals(
                "Nome: Maria Oliveira; CPF: 987.654.321-00",
                service.resolve(fileId, prepared.anonymizedText(), prepared.encryptedMapping())
        );
    }
}
