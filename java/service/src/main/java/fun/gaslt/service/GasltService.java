package fun.gaslt.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import fun.gaslt.sospeso.SospesoClient;
import fun.gaslt.sospeso.SospesoProgram;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.p2p.solanaj.rpc.RpcClient;

/**
 * A small dependency-free HTTP service (JDK {@link HttpServer}) that exposes the
 * gaslt sospeso program's on-chain state as JSON.
 *
 * <pre>
 *   GET /health               liveness + which program/RPC this instance reads
 *   GET /pool/{address}        decode a pool account
 *   GET /registry              decode the singleton bar registry
 *   GET /bridge/{authority}    derive and decode a bridge account
 * </pre>
 *
 * <p>Configuration via environment: {@code PORT} (default 8080) and
 * {@code GASLT_RPC} (default the public Solana mainnet RPC).
 */
public final class GasltService {

    /** Default public mainnet RPC (no API key — safe to ship in an image). */
    public static final String DEFAULT_RPC = "https://api.mainnet-beta.solana.com";

    private final Router router;
    private final int port;
    private HttpServer server;

    public GasltService(int port, String rpc) {
        this.port = port;
        this.router = new Router(new SospesoClient(new RpcClient(rpc)), rpc);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** The bound port (useful when started on port 0 in tests). */
    public int boundPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Router.Response response;
        try {
            response = router.route(exchange.getRequestMethod(), exchange.getRequestURI().getPath());
        } catch (RuntimeException e) {
            response = new Router.Response(500,
                    Json.obj().put("error", "internal").put("message", String.valueOf(e.getMessage())).toString());
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"));
        String rpc = orDefault(System.getenv("GASLT_RPC"), DEFAULT_RPC);
        GasltService service = new GasltService(port, rpc);
        service.start();
        System.out.println("gaslt-service listening on :" + port);
        System.out.println("  program : " + SospesoProgram.PROGRAM_ID.toBase58());
        System.out.println("  rpc     : " + rpc);
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
    }

    private static int parsePort(String env) {
        if (env == null || env.isBlank()) {
            return 8080;
        }
        return Integer.parseInt(env.trim());
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
