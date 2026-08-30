package br.com.geangc.sistema_mr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaddleOcrAvailabilityValidator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaddleOcrAvailabilityValidator.class);

    private final PaddleOcrClient client;

    public PaddleOcrAvailabilityValidator(PaddleOcrClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var health = client.health();
            LOGGER.info("PaddleOCR disponível: model={}, engine={}", health.model(), health.engine());
        } catch (OcrInfrastructureException exception) {
            LOGGER.error(
                    "PaddleOCR é uma dependência mínima da aplicação e não está disponível. "
                            + "O fallback Gemini não será usado para falhas de infraestrutura OCR.",
                    exception
            );
            throw exception;
        }
    }
}
