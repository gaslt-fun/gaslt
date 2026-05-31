package fun.gaslt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

/**
 * Boots the real HTTP server on an ephemeral port and hits {@code /health} (which
 * does not make any RPC call), proving the service starts and serves.
 */
class GasltServiceTest {

    @Test
    void serverStartsAndServesHealth() throws Exception {
        GasltService service = new GasltService(0, GasltService.DEFAULT_RPC);
        service.start();
        try {
            int port = service.boundPort();
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("\"status\":\"ok\""));
            assertEquals("application/json; charset=utf-8", res.headers().firstValue("Content-Type").orElse(""));
        } finally {
            service.stop();
        }
    }
}
