package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.configuration.DocumentPrivacyProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class DocumentMappingCrypto {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DocumentMappingCrypto(DocumentPrivacyProperties properties) {
        if (properties.mappingEncryptionKey() == null || properties.mappingEncryptionKey().isBlank()) {
            throw new IllegalStateException("DOCUMENT_MAPPING_ENCRYPTION_KEY é obrigatório");
        }
        this.key = new SecretKeySpec(sha256(properties.mappingEncryptionKey()), "AES");
    }

    public byte[] encrypt(String plaintext, String fileId) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(fileId.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_LENGTH + ciphertext.length)
                    .put(FORMAT_VERSION)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível criptografar o mapping documental", exception);
        }
    }

    public String decrypt(byte[] encrypted, String fileId) {
        if (encrypted == null || encrypted.length <= 1 + NONCE_LENGTH) {
            throw new IllegalArgumentException("Mapping documental criptografado inválido");
        }
        if (encrypted[0] != FORMAT_VERSION) {
            throw new IllegalArgumentException("Versão de mapping documental não suportada");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(encrypted, 1, 1 + NONCE_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, 1 + NONCE_LENGTH, encrypted.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(fileId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível descriptografar o mapping documental", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
