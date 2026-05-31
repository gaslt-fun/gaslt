package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

class AccountDecodeTest {

    private static final PublicKey SPONSOR =
            new PublicKey("So11111111111111111111111111111111111111112");
    private static final PublicKey BENEFICIARY =
            new PublicKey("ComputeBudget111111111111111111111111111111");

    /**
     * Build a Sospeso account buffer exactly as the on-chain layout serialises it
     * (the v0.1.3 layout published in the IDL: no target-program field, with
     * {@code new_wallet_only} preceding {@code nonce}).
     */
    private static byte[] encodeSospeso(
            long lamportsTotal,
            long lamportsRemaining,
            long maxPerClaim,
            long claimsCount,
            long maxClaims,
            long expiryTs,
            long nonce,
            boolean newWalletOnly,
            int bump) {
        return new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("Sospeso"))
                .pubkey(SPONSOR)
                .u64(lamportsTotal)
                .u64(lamportsRemaining)
                .u64(maxPerClaim)
                .u32(claimsCount)
                .u32(maxClaims)
                .i64(expiryTs)
                .bool(newWalletOnly)
                .u64(nonce)
                .u8(bump)
                .toByteArray();
    }

    @Test
    void decodesSospesoFields() {
        byte[] data = encodeSospeso(
                1_000_000_000L, 750_000_000L, 5_000_000L, 3, 10, 1_900_000_000L, 42, true, 254);
        assertEquals(Sospeso.ACCOUNT_SIZE, data.length);
        assertEquals(90, data.length);

        Sospeso pool = Sospeso.from(data);
        assertEquals(SPONSOR, pool.sponsor());
        assertEquals(1_000_000_000L, pool.lamportsTotal());
        assertEquals(750_000_000L, pool.lamportsRemaining());
        assertEquals(5_000_000L, pool.maxPerClaim());
        assertEquals(3, pool.claimsCount());
        assertEquals(10, pool.maxClaims());
        assertEquals(1_900_000_000L, pool.expiryTs());
        assertEquals(42, pool.nonce());
        assertTrue(pool.newWalletOnly());
        assertEquals(254, pool.bump());
    }

    @Test
    void utilizationAndPredicatesMatchCore() {
        // 250M of 1000M spent -> 25% utilisation = 2500 bps.
        Sospeso pool = Sospeso.from(encodeSospeso(
                1_000_000_000L, 750_000_000L, 300_000_000L, 1, 0, 100, 1, false, 255));
        assertEquals(2_500, pool.utilizationBps());

        assertTrue(pool.canCover(300_000_000L, 50));
        assertFalse(pool.canCover(300_000_001L, 50)); // over per-claim cap
        assertFalse(pool.canCover(300_000_000L, 100)); // expired (expiry <= now)
        assertFalse(pool.canCover(0, 50));            // zero amount
        assertTrue(pool.isExpired(100));
        assertFalse(pool.isExpired(99));
    }

    @Test
    void claimsExhaustedWhenCountReached() {
        Sospeso full = Sospeso.from(encodeSospeso(100, 100, 0, 5, 5, 0, 1, false, 255));
        assertTrue(full.claimsExhausted());
        Sospeso room = Sospeso.from(encodeSospeso(100, 100, 0, 4, 5, 0, 1, false, 255));
        assertFalse(room.claimsExhausted());
        Sospeso unbounded = Sospeso.from(encodeSospeso(100, 100, 0, 9999, 0, 0, 1, false, 255));
        assertFalse(unbounded.claimsExhausted());
    }

    @Test
    void rejectsWrongDiscriminator() {
        byte[] data = encodeSospeso(1, 1, 0, 0, 0, 0, 1, false, 255);
        data[0] ^= 0xFF; // corrupt the discriminator
        assertThrows(IllegalArgumentException.class, () -> Sospeso.from(data));
    }

    @Test
    void decodesClaimReceipt() {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("ClaimReceipt"))
                .pubkey(BENEFICIARY)
                .u64(5_000_000L)
                .i64(1_800_000_000L)
                .u8(253)
                .toByteArray();
        assertEquals(ClaimReceipt.ACCOUNT_SIZE, data.length);

        ClaimReceipt receipt = ClaimReceipt.from(data);
        assertEquals(BENEFICIARY, receipt.beneficiary());
        assertEquals(5_000_000L, receipt.amount());
        assertEquals(1_800_000_000L, receipt.ts());
        assertEquals(253, receipt.bump());
    }
}
