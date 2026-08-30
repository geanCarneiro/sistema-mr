package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
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

class PaddleOcrClientTest {

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
    void sendsJsonWithAnExplicitContentLength() throws Exception {
        AtomicInteger declaredLength = new AtomicInteger();
        AtomicInteger receivedLength = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> respond(
                exchange,
                "{\"status\":\"UP\",\"ready\":true,\"model\":\"PP-OCRv6_medium\",\"engine\":\"onnxruntime\"}"
        ));
        server.createContext("/ocr", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            declaredLength.set(Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-Length")));
            receivedLength.set(body.length);
            respond(exchange, "{\"model\":\"PP-OCRv6_medium\",\"lines\":[],\"meanConfidence\":0.0,\"durationMs\":1}");
        });
        server.start();

        Path image = Files.write(temporaryDirectory.resolve("poster.png"), new byte[]{1, 2, 3});
        PaddleOcrClient client = new PaddleOcrClient(properties(server.getAddress().getPort()), new ObjectMapper());

        PaddleOcrClient.OcrResult result = client.extract(image, "poster.png", "image/png");

        assertEquals("PP-OCRv6_medium", result.model());
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
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, 200_000,
                new DocumentProperties.Ocr("http://127.0.0.1:" + port, 5, 12, .55)
        );
    }
}
