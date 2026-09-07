package br.com.geangc.sistema_mr.privacy;

import br.com.geangc.sistema_mr.model.DocumentSensitivity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnonymizer {

    private static final List<SemanticDetector> SEMANTIC_DETECTORS = List.of(
            new SemanticDetector(DocumentSensitivity.SENSITIVE,
                    Pattern.compile("(?i)\\b(?:saúde|saude|médic[oa]|medicamento|diagnóstico|diagnostico|paciente|exame|tratamento|terapia|psicol[oó]gic)\\b")),
            new SemanticDetector(DocumentSensitivity.RESTRICTED,
                    Pattern.compile("(?i)\\b(?:senha|credencial|segredo|conta bancária|conta bancaria|cartão de crédito|cartao de credito|salário|salario|renda)\\b"))
    );

    private static final List<Detector> DETECTORS = List.of(
            new Detector("CPF", "CPF", DocumentSensitivity.PERSONAL,
                    Pattern.compile("\\b\\d{3}[. ]?\\d{3}[. ]?\\d{3}[- ]?\\d{2}\\b")),
            new Detector("EMAIL", "E-mail", DocumentSensitivity.PERSONAL,
                    Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b")),
            new Detector("PHONE", "Telefone", DocumentSensitivity.PERSONAL,
                    Pattern.compile("(?<!\\d)(?:\\+?55[ .-]?)?(?:\\(?\\d{2}\\)?[ .-]?)?9?\\d{4}[ .-]?\\d{4}(?!\\d)")),
            new Detector("CARD", "Cartão", DocumentSensitivity.RESTRICTED,
                    Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")),
            new Detector("API_KEY", "Credencial", DocumentSensitivity.RESTRICTED,
                    Pattern.compile("\\b(?:sk|pk|api|token|secret)[_-][A-Za-z0-9_-]{12,}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("NAMED_PERSON", "Pessoa", DocumentSensitivity.PERSONAL,
                    Pattern.compile("(?i)\\b(?:nome|paciente|cliente|titular)\\s*:\\s*([\\p{L}][\\p{L}'-]*(?:\\s+[\\p{L}][\\p{L}'-]*){1,5})"), 1)
    );

    public AnonymizationResult anonymize(String text) {
        if (text == null || text.isBlank()) {
            return new AnonymizationResult(text == null ? "" : text, List.of(), "deterministic-v2",
                    DocumentSensitivity.NORMAL);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Detector detector : DETECTORS) {
            Matcher matcher = detector.pattern().matcher(text);
            while (matcher.find()) {
                int start = detector.group() == 0 ? matcher.start() : matcher.start(detector.group());
                int end = detector.group() == 0 ? matcher.end() : matcher.end(detector.group());
                String value = text.substring(start, end);
                if (isMeaningful(detector.type(), value)) {
                    candidates.add(new Candidate(start, end, detector.type(), detector.label(),
                            detector.sensitivity(), value));
                }
            }
        }

        candidates.sort(Comparator.comparingInt(Candidate::start)
                .thenComparing(Comparator.comparingInt(Candidate::length).reversed()));

        List<Candidate> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlaps = accepted.stream().anyMatch(existing ->
                    candidate.start() < existing.end() && existing.start() < candidate.end());
            if (!overlaps) {
                accepted.add(candidate);
            }
        }
        accepted.sort(Comparator.comparingInt(Candidate::start));

        Map<String, Integer> sequenceByType = new LinkedHashMap<>();
        List<DocumentSpan> spans = new ArrayList<>(accepted.size());
        for (Candidate candidate : accepted) {
            int sequence = sequenceByType.merge(candidate.type(), 1, Integer::sum);
            String token = "<" + candidate.type() + "_" + String.format("%03d", sequence) + ">";
            spans.add(new DocumentSpan(candidate.start(), candidate.end(), candidate.label(),
                    candidate.sensitivity(), token, candidate.value()));
        }

        StringBuilder anonymized = new StringBuilder(text.length());
        int cursor = 0;
        for (DocumentSpan span : spans) {
            anonymized.append(text, cursor, span.start()).append(span.token());
            cursor = span.end();
        }
        anonymized.append(text, cursor, text.length());
        DocumentSensitivity highest = spans.stream()
                .map(DocumentSpan::sensitivity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(DocumentSensitivity.NORMAL);
        for (SemanticDetector detector : SEMANTIC_DETECTORS) {
            if (detector.pattern().matcher(text).find()
                    && detector.sensitivity().ordinal() > highest.ordinal()) {
                highest = detector.sensitivity();
            }
        }
        return new AnonymizationResult(anonymized.toString(), spans, "deterministic-v2", highest);
    }

    private static boolean isMeaningful(String type, String value) {
        return switch (type) {
            case "PHONE" -> value.replaceAll("\\D", "").length() >= 8;
            case "CARD" -> value.replaceAll("\\D", "").length() >= 13;
            default -> !value.isBlank();
        };
    }

    private record Detector(
            String type,
            String label,
            DocumentSensitivity sensitivity,
            Pattern pattern,
            int group
    ) {
        private Detector(String type, String label, DocumentSensitivity sensitivity, Pattern pattern) {
            this(type, label, sensitivity, pattern, 0);
        }
    }

    private record SemanticDetector(DocumentSensitivity sensitivity, Pattern pattern) {}

    private record Candidate(
            int start,
            int end,
            String type,
            String label,
            DocumentSensitivity sensitivity,
            String value
    ) {
        private int length() {
            return end - start;
        }
    }
}
