package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

class PdaTest {

    private static final PublicKey SPONSOR =
            new PublicKey("So11111111111111111111111111111111111111112");
    private static final PublicKey BENEFICIARY =
            new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    @Test
    void nonceEncodesLittleEndianU64() {
        assertEquals("0100000000000000", HexFormat.of().formatHex(Pda.nonceLeBytes(1)));
        assertEquals("ff00000000000000", HexFormat.of().formatHex(Pda.nonceLeBytes(255)));
        assertEquals("0001000000000000", HexFormat.of().formatHex(Pda.nonceLeBytes(256)));
    }

    @Test
    void sospesoPdaIsDeterministic() {
        PublicKey a = Pda.sospeso(SPONSOR, 7).getAddress();
        PublicKey b = Pda.sospeso(SPONSOR, 7).getAddress();
        assertEquals(a, b);
    }

    @Test
    void differentNonceYieldsDifferentPda() {
        assertNotEquals(
                Pda.sospeso(SPONSOR, 1).getAddress(),
                Pda.sospeso(SPONSOR, 2).getAddress());
    }

    @Test
    void sospesoPdaUsesExpectedSeeds() {
        // Re-derive with the seeds spelled out to confirm Pda passes the right ones.
        PublicKey expected = PublicKey.findProgramAddress(
                List.of(
                        "sospeso".getBytes(),
                        SPONSOR.toByteArray(),
                        Pda.nonceLeBytes(42)),
                SospesoProgram.PROGRAM_ID).getAddress();
        assertEquals(expected, Pda.sospeso(SPONSOR, 42).getAddress());
    }

    @Test
    void claimReceiptPdaUsesExpectedSeeds() {
        PublicKey pool = Pda.sospeso(SPONSOR, 3).getAddress();
        PublicKey expected = PublicKey.findProgramAddress(
                List.of(
                        "claim".getBytes(),
                        pool.toByteArray(),
                        BENEFICIARY.toByteArray()),
                SospesoProgram.PROGRAM_ID).getAddress();
        assertEquals(expected, Pda.claimReceipt(pool, BENEFICIARY).getAddress());
    }

    @Test
    void claimReceiptIsBeneficiarySpecific() {
        PublicKey pool = Pda.sospeso(SPONSOR, 3).getAddress();
        assertNotEquals(
                Pda.claimReceipt(pool, BENEFICIARY).getAddress(),
                Pda.claimReceipt(pool, SPONSOR).getAddress());
    }

    @Test
    void seedPrefixesMatchProgram() {
        assertArrayEquals("sospeso".getBytes(), Pda.SOSPESO_SEED);
        assertArrayEquals("claim".getBytes(), Pda.CLAIM_SEED);
        assertTrue(Pda.sospeso(SPONSOR, 0).getNonce() <= 255);
    }
}
