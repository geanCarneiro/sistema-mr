package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LocalDocumentEmbeddingProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsEmbeddingJsonWithAnExplicitContentLength() throws Exception {
        AtomicInteger declaredLength = new AtomicInteger();
        AtomicInteger receivedLength = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            declaredLength.set(Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-Length")));
            receivedLength.set(body.length);
            respond(exchange, "{\"model\":\"test\",\"embeddings\":[[0.1,0.2]]}");
        });
        server.start();

        LocalDocumentEmbeddingProvider provider = new LocalDocumentEmbeddingProvider(
                properties(server.getAddress().getPort()), new ObjectMapper()
        );

        var embeddings = provider.embed(java.util.List.of("texto de teste"));

        assertEquals(1, embeddings.size());
        assertEquals(2, embeddings.getFirst().length);
        assertTrue(declaredLength.get() > 0);
        assertEquals(declaredLength.get(), receivedLength.get());
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static DocumentProperties properties(int port) {
        return new DocumentProperties(
                java.nio.file.Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "test", 2, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 5, 12, .55),
                "http://127.0.0.1:" + port
        );
    }
}
