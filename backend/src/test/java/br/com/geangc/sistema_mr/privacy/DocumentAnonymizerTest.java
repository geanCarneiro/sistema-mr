package br.com.geangc.sistema_mr.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.model.DocumentSensitivity;
import org.junit.jupiter.api.Test;

class DocumentAnonymizerTest {

    private final DocumentAnonymizer anonymizer = new DocumentAnonymizer();

    @Test
    void replacesIdentifiersWithTokensScopedByCategory() {
        AnonymizationResult result = anonymizer.anonymize(
                "Nome: João da Silva; CPF: 123.456.789-00; e-mail: joao@example.com"
        );

        assertEquals(
                "Nome: <NAMED_PERSON_001>; CPF: <CPF_001>; e-mail: <EMAIL_001>",
                result.anonymizedText()
        );
        assertEquals(3, result.spans().size());
        assertEquals(DocumentSensitivity.PERSONAL, result.spans().getFirst().sensitivity());
        assertFalse(result.anonymizedText().contains("João da Silva"));
        assertFalse(result.anonymizedText().contains("123.456.789-00"));
    }

    @Test
    void preservesOperationalValuesAndDoesNotTokenizeOrdinaryText() {
        AnonymizationResult result = anonymizer.anonymize(
                "Total da compra: R$ 125,00. A compra foi realizada ontem."
        );

        assertEquals("Total da compra: R$ 125,00. A compra foi realizada ontem.", result.anonymizedText());
        assertTrue(result.spans().isEmpty());
    }

    @Test
    void classifiesSensitiveSemanticTermsWithoutExposingTheirValues() {
        AnonymizationResult result = anonymizer.anonymize(
                "Resultado do exame médico e orientação de tratamento."
        );

        assertEquals(DocumentSensitivity.SENSITIVE, result.highestSensitivity());
    }
}
