package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

class ModelTest {

    private static final PublicKey SPONSOR =
            new PublicKey("So11111111111111111111111111111111111111112");

    @Test
    void sospesoIdDerivationIsDeterministic() {
        SospesoId a = SospesoId.derive(SPONSOR, 3);
        SospesoId b = SospesoId.derive(SPONSOR, 3);
        assertEquals(a, b);
        assertTrue(a.value().startsWith("spo_"));
        assertTrue(a.value().endsWith("_3"));
    }

    @Test
    void sospesoIdVariesByNonce() {
        assertTrue(!SospesoId.derive(SPONSOR, 1).equals(SospesoId.derive(SPONSOR, 2)));
    }

    @Test
    void lamportConversions() {
        assertEquals(1_000_000_000L, Lamports.LAMPORTS_PER_SOL);
        assertEquals(1.5, Lamports.toSol(1_500_000_000L));
        assertEquals(2_000_000_000L, Lamports.fromSol(2.0));
    }

    @Test
    void paramsBuilderSetsFields() {
        SospesoParams p = SospesoParams.of(SPONSOR, 1_000)
                .withMaxPerClaim(100)
                .withMaxClaims(5)
                .withExpiry(42)
                .newWalletsOnly();
        assertEquals(100, p.maxPerClaim());
        assertEquals(5, p.maxClaims());
        assertEquals(42, p.expiryTs());
        assertTrue(p.newWalletOnly());
        assertEquals(1_000, p.lamportsTotal());
    }
}
