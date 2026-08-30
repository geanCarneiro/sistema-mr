package br.com.geangc.sistema_mr.configuration;

import java.util.List;

public record DocumentVisionResponse(
        String transcription,
        String visualDescription,
        List<String> uncertainSegments,
        List<String> detectedLanguages
) {
    public static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "transcription": {"type": "string"},
                "visualDescription": {"type": "string"},
                "uncertainSegments": {
                  "type": "array",
                  "items": {"type": "string"}
                },
                "detectedLanguages": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": [
                "transcription",
                "visualDescription",
                "uncertainSegments",
                "detectedLanguages"
              ],
              "additionalProperties": false
            }
            """;

    public DocumentVisionResponse {
        transcription = transcription == null ? "" : transcription.strip();
        visualDescription = visualDescription == null ? "" : visualDescription.strip();
        uncertainSegments = uncertainSegments == null ? List.of() : List.copyOf(uncertainSegments);
        detectedLanguages = detectedLanguages == null ? List.of() : List.copyOf(detectedLanguages);
    }
}
