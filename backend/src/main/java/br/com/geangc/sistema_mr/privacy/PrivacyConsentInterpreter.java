package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.agent.gateway.LocalModelProvider;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PrivacyConsentInterpreter {

    private final LocalModelProvider localModelProvider;

    public PrivacyConsentInterpreter(LocalModelProvider localModelProvider) {
        this.localModelProvider = localModelProvider;
    }

    public Consent interpret(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "não", "nao", "nunca", "não envie", "nao envie", "pare")) {
            return new Consent("DENY", "A pessoa não autorizou o envio", 1.0);
        }
        if (containsAny(normalized, "documento inteiro", "conteúdo completo", "conteudo completo", "tudo")) {
            return new Consent("ALLOW_FULL", "A pessoa autorizou o conteúdo completo", 0.95);
        }
        if (containsAny(normalized, "sim", "pode", "anonimizado", "somente o necessário", "somente o necessario")) {
            return new Consent("ALLOW_MINIMIZED", "A pessoa autorizou a representação minimizada", 0.9);
        }
        if (localModelProvider == null) {
            return new Consent("CLARIFY", "Não foi possível interpretar a autorização localmente", 0.0);
        }
        try {
            LocalModelProvider.LocalDecision decision = localModelProvider.decide(prompt);
            return new Consent(decision.intent(), decision.explanation(), decision.confidence());
        } catch (RuntimeException exception) {
            return new Consent("CLARIFY", "A decisão local está indisponível", 0.0);
        }
    }

    /**
     * Interpreta somente a resposta natural à pergunta de revisão de arquivo.
     * O prompt enviado ao modelo local não contém o conteúdo do arquivo.
     */
    public Review interpretReview(String prompt) {
        if (localModelProvider == null) {
            return new Review("CLARIFY", "A interpretação local está indisponível", 0.0);
        }
        String classificationPrompt = """
                Classifique a resposta do usuário para a pergunta: "Você prefere que eu tente processar novamente o arquivo ou que eu o mantenha bloqueado?"
                Resposta do usuário: %s
                Retorne a intenção RETRY_ANALYSIS se ele autorizou uma nova tentativa de processamento, KEEP_BLOCKED se pediu para manter bloqueado, ou CLARIFY se ficou ambíguo.
                Não invente uma autorização e não considere o conteúdo de nenhum arquivo.
                """.formatted(prompt == null ? "" : prompt);
        try {
            LocalModelProvider.LocalDecision decision = localModelProvider.decide(classificationPrompt);
            String intent = switch (decision.intent()) {
                case "RETRY_ANALYSIS", "KEEP_BLOCKED", "CLARIFY" -> decision.intent();
                default -> "CLARIFY";
            };
            return new Review(intent, decision.explanation(), decision.confidence());
        } catch (RuntimeException exception) {
            return new Review("CLARIFY", "A decisão local está indisponível", 0.0);
        }
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public record Consent(String intent, String explanation, double confidence) {}

    public record Review(String intent, String explanation, double confidence) {}
}
