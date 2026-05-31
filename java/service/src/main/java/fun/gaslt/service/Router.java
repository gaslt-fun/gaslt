package fun.gaslt.service;

import java.util.Optional;

import fun.gaslt.sospeso.BarRegistry;
import fun.gaslt.sospeso.Bridge;
import fun.gaslt.sospeso.Lamports;
import fun.gaslt.sospeso.Pda;
import fun.gaslt.sospeso.Sospeso;
import fun.gaslt.sospeso.SospesoClient;
import fun.gaslt.sospeso.SospesoProgram;

import org.p2p.solanaj.core.PublicKey;

/**
 * Maps HTTP method + path to a JSON {@link Response}, independent of the HTTP
 * server, so the routing logic is unit-testable without binding a socket. The
 * RPC-backed routes use a {@link SospesoClient}; {@code /health} does not touch
 * the network.
 */
public final class Router {

    /** Program version this service decodes. */
    public static final String VERSION = "0.1.3";

    private final SospesoClient client;
    private final String rpc;

    public Router(SospesoClient client, String rpc) {
        this.client = client;
        this.rpc = rpc;
    }

    /** A status code plus a JSON body. */
    public record Response(int status, String body) {
    }

    public Response route(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) {
            return json(405, Json.obj().put("error", "method_not_allowed").put("method", method));
        }
        if (path.equals("/health") || path.equals("/")) {
            return health();
        }
        if (path.startsWith("/pool/")) {
            return pool(tail(path, "/pool/"));
        }
        if (path.equals("/registry")) {
            return registry();
        }
        if (path.startsWith("/bridge/")) {
            return bridge(tail(path, "/bridge/"));
        }
        return json(404, Json.obj().put("error", "not_found").put("path", path));
    }

    private Response health() {
        return json(200, Json.obj()
                .put("status", "ok")
                .put("service", "gaslt-service")
                .put("version", VERSION)
                .put("program", SospesoProgram.PROGRAM_ID.toBase58())
                .put("rpc", rpc));
    }

    private Response pool(String address) {
        PublicKey addr;
        try {
            addr = new PublicKey(address);
        } catch (RuntimeException e) {
            return json(400, Json.obj().put("error", "invalid_address").put("address", address));
        }
        try {
            Optional<Sospeso> found = client.fetchSospeso(addr);
            if (found.isEmpty()) {
                return json(404, Json.obj().put("error", "pool_not_found").put("address", address));
            }
            Sospeso p = found.get();
            long now = System.currentTimeMillis() / 1000L;
            return json(200, Json.obj()
                    .put("address", address)
                    .put("sponsor", p.sponsor().toBase58())
                    .put("lamportsTotal", p.lamportsTotal())
                    .put("lamportsRemaining", p.lamportsRemaining())
                    .put("solRemaining", Lamports.toSol(p.lamportsRemaining()))
                    .put("maxPerClaim", p.maxPerClaim())
                    .put("claimsCount", p.claimsCount())
                    .put("maxClaims", p.maxClaims())
                    .put("expiryTs", p.expiryTs())
                    .put("newWalletOnly", p.newWalletOnly())
                    .put("utilizationBps", p.utilizationBps())
                    .put("expired", p.isExpired(now)));
        } catch (Exception e) {
            return rpcError(e);
        }
    }

    private Response registry() {
        PublicKey pda = Pda.registry().getAddress();
        try {
            Optional<BarRegistry> found = client.fetchRegistry();
            if (found.isEmpty()) {
                return json(404, Json.obj()
                        .put("error", "registry_not_initialised")
                        .put("registryPda", pda.toBase58()));
            }
            BarRegistry r = found.get();
            return json(200, Json.obj()
                    .put("registryPda", pda.toBase58())
                    .put("authority", r.authority().toBase58())
                    .put("totalPools", r.totalPools())
                    .put("totalClaims", r.totalClaims())
                    .put("totalSuspendedLamports", r.totalSuspendedLamports().toString())
                    .put("createdTs", r.createdTs())
                    .put("lastUpdateTs", r.lastUpdateTs()));
        } catch (Exception e) {
            return rpcError(e);
        }
    }

    private Response bridge(String authority) {
        PublicKey auth;
        try {
            auth = new PublicKey(authority);
        } catch (RuntimeException e) {
            return json(400, Json.obj().put("error", "invalid_authority").put("authority", authority));
        }
        PublicKey pda = Pda.bridge(auth).getAddress();
        try {
            Optional<Bridge> found = client.fetchBridge(auth);
            if (found.isEmpty()) {
                return json(404, Json.obj()
                        .put("error", "bridge_not_registered")
                        .put("authority", authority)
                        .put("bridgePda", pda.toBase58()));
            }
            Bridge b = found.get();
            return json(200, Json.obj()
                    .put("authority", authority)
                    .put("bridgePda", pda.toBase58())
                    .put("kind", b.kind())
                    .put("endpoint", b.endpoint())
                    .put("label", b.label())
                    .put("enabled", b.enabled())
                    .put("registeredAt", b.registeredAt())
                    .put("updatedAt", b.updatedAt()));
        } catch (Exception e) {
            return rpcError(e);
        }
    }

    private Response rpcError(Exception e) {
        return json(502, Json.obj().put("error", "rpc_error").put("message", String.valueOf(e.getMessage())));
    }

    private static Response json(int status, Json body) {
        return new Response(status, body.toString());
    }

    private static String tail(String path, String prefix) {
        return path.substring(prefix.length());
    }
}
