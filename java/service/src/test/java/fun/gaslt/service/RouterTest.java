package fun.gaslt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fun.gaslt.sospeso.SospesoClient;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.rpc.RpcClient;

/**
 * Offline routing tests. The {@code /health}, not-found, and validation paths
 * never touch the network, so they run without an RPC connection.
 */
class RouterTest {

    private Router router() {
        // The client is constructed but never called by these routes.
        return new Router(new SospesoClient(new RpcClient("https://api.mainnet-beta.solana.com")),
                "https://api.mainnet-beta.solana.com");
    }

    @Test
    void healthIsOkAndDescribesProgram() {
        Router.Response r = router().route("GET", "/health");
        assertEquals(200, r.status());
        assertTrue(r.body().contains("\"status\":\"ok\""));
        assertTrue(r.body().contains("44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW"));
        assertTrue(r.body().contains("\"version\":\"0.1.3\""));
    }

    @Test
    void unknownPathIs404() {
        Router.Response r = router().route("GET", "/nope");
        assertEquals(404, r.status());
        assertTrue(r.body().contains("not_found"));
    }

    @Test
    void nonGetIs405() {
        Router.Response r = router().route("POST", "/health");
        assertEquals(405, r.status());
    }

    @Test
    void invalidPoolAddressIs400() {
        Router.Response r = router().route("GET", "/pool/not-a-valid-base58-key!!!");
        assertEquals(400, r.status());
        assertTrue(r.body().contains("invalid_address"));
    }

    @Test
    void invalidBridgeAuthorityIs400() {
        Router.Response r = router().route("GET", "/bridge/!!!notvalid");
        assertEquals(400, r.status());
        assertTrue(r.body().contains("invalid_authority"));
    }
}
