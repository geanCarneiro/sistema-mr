package br.com.geangc.sistema_mr.state.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FlexiblePayloadCodecTest {

    private final FlexiblePayloadCodec codec = new FlexiblePayloadCodec(new ObjectMapper());

    @Test
    void acceptsJsonAndYamlWithTheSameStructuredResult() {
        Map<String, Object> json = codec.parse("{\"status\":\"open\",\"priority\":2}");
        Map<String, Object> yaml = codec.parse("status: open\npriority: 2\n");

        assertEquals(json, yaml);
        assertEquals("{\"status\":\"open\",\"priority\":2}", codec.write(yaml));
    }
}
