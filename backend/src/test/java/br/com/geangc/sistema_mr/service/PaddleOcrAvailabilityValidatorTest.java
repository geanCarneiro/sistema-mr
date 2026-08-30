package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PaddleOcrAvailabilityValidatorTest {

    @Test
    void acceptsAReadyLocalOcrService() {
        PaddleOcrClient client = mock(PaddleOcrClient.class);
        when(client.health()).thenReturn(new PaddleOcrClient.Health(
                "UP", true, "PP-OCRv6_medium", "onnxruntime"
        ));

        assertDoesNotThrow(() -> new PaddleOcrAvailabilityValidator(client)
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void failsApplicationStartupWhenLocalOcrIsUnavailable() {
        PaddleOcrClient client = mock(PaddleOcrClient.class);
        when(client.health()).thenThrow(new OcrInfrastructureException("indisponível"));

        assertThrows(
                OcrInfrastructureException.class,
                () -> new PaddleOcrAvailabilityValidator(client).run(new DefaultApplicationArguments())
        );
    }
}
