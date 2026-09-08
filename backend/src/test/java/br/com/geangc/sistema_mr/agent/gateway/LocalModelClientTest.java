package br.com.geangc.sistema_mr.agent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.configuration.LocalAiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class LocalModelClientTest {

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsVisionAsBinaryWithContentMetadata() throws Exception {
        AtomicInteger declaredLength = new AtomicInteger();
        AtomicInteger receivedLength = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vision", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            declaredLength.set(Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-Length")));
            receivedLength.set(body.length);
            assertEquals("image/png", exchange.getRequestHeaders().getFirst("Content-Type"));
            assertEquals("Descreva a imagem", exchange.getRequestHeaders().getFirst("X-Prompt"));
            assertEquals("256", exchange.getRequestHeaders().getFirst("X-Max-Tokens"));
            respond(exchange, "{\"content\":\"imagem processada\"}");
        });
        server.start();

        byte[] expected = {1, 2, 3};
        Path image = Files.write(temporaryDirectory.resolve("poster.png"), expected);
        LocalModelClient client = new LocalModelClient(properties(server.getAddress().getPort()), new ObjectMapper());

        LocalModelProvider.LocalVision result = client.vision(image, "image/png", "Descreva a imagem");

        assertEquals("imagem processada", result.content());
        assertTrue(declaredLength.get() > 0);
        assertEquals(declaredLength.get(), receivedLength.get());
        assertEquals(expected.length, receivedLength.get());
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static LocalAiProperties properties(int port) {
        return new LocalAiProperties(
                "http://127.0.0.1:" + port,
                "gemma-3-4b-it-q4",
                2,
                5,
                256,
                256
        );
    }
}
