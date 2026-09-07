package br.com.geangc.sistema_mr.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.geangc.sistema_mr.configuration.DocumentPrivacyProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentMappingCryptoTest {

    @Test
    void encryptsAndDecryptsMappingWithFileScopedAssociatedData() {
        DocumentMappingCrypto crypto = new DocumentMappingCrypto(new DocumentPrivacyProperties("mvp-key"));
        String fileId = UUID.randomUUID().toString();
        byte[] encrypted = crypto.encrypt("{\"value\":\"João\"}", fileId);

        assertEquals("{\"value\":\"João\"}", crypto.decrypt(encrypted, fileId));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(encrypted, UUID.randomUUID().toString()));
    }

    @Test
    void usesRandomNonceForEqualMappings() {
        DocumentMappingCrypto crypto = new DocumentMappingCrypto(new DocumentPrivacyProperties("mvp-key"));
        String fileId = UUID.randomUUID().toString();

        byte[] first = crypto.encrypt("same", fileId);
        byte[] second = crypto.encrypt("same", fileId);

        assertEquals("same", crypto.decrypt(first, fileId));
        assertEquals("same", crypto.decrypt(second, fileId));
        assertEquals(first.length, second.length);
        assertFalse(java.util.Arrays.equals(first, second));
    }
}
