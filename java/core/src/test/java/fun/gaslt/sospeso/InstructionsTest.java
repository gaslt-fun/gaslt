package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;

class InstructionsTest {

    private static final PublicKey SPONSOR =
            new PublicKey("So11111111111111111111111111111111111111112");
    private static final PublicKey PAYER =
            new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    private static final PublicKey BENEFICIARY =
            new PublicKey("ComputeBudget111111111111111111111111111111");

    private static byte[] head8(byte[] data) {
        return Arrays.copyOfRange(data, 0, 8);
    }

    @Test
    void createSospesoEncodesArgsAndAccounts() {
        SospesoParams params = SospesoParams.of(SPONSOR, 2_000_000_000L)
                .withMaxPerClaim(5_000_000L)
                .withMaxClaims(100)
                .withExpiry(1_900_000_000L)
                .newWalletsOnly();
        long nonce = 7;

        TransactionInstruction ix = SospesoInstructions.createSospeso(params, nonce);

        assertEquals(SospesoProgram.PROGRAM_ID, ix.getProgramId());
        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("create_sospeso"), head8(ix.getData()));
        // 8 disc + u64 + u64 + u64 + u32 + i64 + bool = 8 + 8+8+8+4+8+1 = 45
        assertEquals(45, ix.getData().length);

        // Decode the args back and check they round-trip.
        BorshReader r = new BorshReader(ix.getData()).skip(8);
        assertEquals(nonce, r.u64());
        assertEquals(2_000_000_000L, r.u64());
        assertEquals(5_000_000L, r.u64());
        assertEquals(100, r.u32());
        assertEquals(1_900_000_000L, r.i64());
        assertTrue(r.bool());

        List<AccountMeta> keys = ix.getKeys();
        // sponsor, pool PDA, system program -- no target-program account (IDL v0.1.3).
        assertEquals(3, keys.size());
        // sponsor: signer + writable
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        // pool PDA: writable, not signer, matches derivation
        assertEquals(Pda.sospeso(SPONSOR, nonce).getAddress(), keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isSigner());
        assertTrue(keys.get(1).isWritable());
        // system program: readonly
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(2).getPublicKey());
        assertFalse(keys.get(2).isSigner());
        assertFalse(keys.get(2).isWritable());
    }

    @Test
    void claimSospesoEncodesAmountAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 1).getAddress();
        TransactionInstruction ix =
                SospesoInstructions.claimSospeso(PAYER, BENEFICIARY, pool, 1_234_567L);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("claim_sospeso"), head8(ix.getData()));
        assertEquals(16, ix.getData().length); // 8 + u64
        assertEquals(1_234_567L, new BorshReader(ix.getData()).skip(8).u64());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(5, keys.size());
        assertEquals(PAYER, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        // beneficiary: writable but NOT a signer (a relayer can claim on its behalf)
        assertEquals(BENEFICIARY, keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isSigner());
        assertTrue(keys.get(1).isWritable());
        assertEquals(pool, keys.get(2).getPublicKey());
        assertTrue(keys.get(2).isWritable());
        // receipt PDA matches derivation, writable
        assertEquals(Pda.claimReceipt(pool, BENEFICIARY).getAddress(), keys.get(3).getPublicKey());
        assertTrue(keys.get(3).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(4).getPublicKey());
        assertFalse(keys.get(4).isWritable());
    }

    @Test
    void topUpEncodesAmountAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 2).getAddress();
        TransactionInstruction ix = SospesoInstructions.topUp(SPONSOR, pool, 9_000_000L);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("top_up"), head8(ix.getData()));
        assertEquals(16, ix.getData().length);
        assertEquals(9_000_000L, new BorshReader(ix.getData()).skip(8).u64());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(3, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(2).getPublicKey());
    }

    @Test
    void reclaimEncodesDiscriminatorOnly() {
        PublicKey pool = Pda.sospeso(SPONSOR, 5).getAddress();
        TransactionInstruction ix = SospesoInstructions.reclaim(SPONSOR, pool);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("reclaim"), ix.getData());
        assertEquals(8, ix.getData().length); // discriminator only, no args

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(2, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
    }

    @Test
    void setMetaEncodesStringsAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 3).getAddress();
        TransactionInstruction ix = SospesoInstructions.setMeta(
                SPONSOR, pool, "Napoli", "Free first gas", "https://gaslt.fun/p/1", 2);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("set_meta"), head8(ix.getData()));
        BorshReader r = new BorshReader(ix.getData()).skip(8);
        assertEquals("Napoli", r.string());
        assertEquals("Free first gas", r.string());
        assertEquals("https://gaslt.fun/p/1", r.string());
        assertEquals(2, r.u8());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(4, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        // pool: readonly
        assertEquals(pool, keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isSigner());
        assertFalse(keys.get(1).isWritable());
        // meta PDA: writable
        assertEquals(Pda.meta(pool).getAddress(), keys.get(2).getPublicKey());
        assertTrue(keys.get(2).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(3).getPublicKey());
        assertFalse(keys.get(3).isWritable());
    }

    @Test
    void updateMetaEncodesStringsAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 3).getAddress();
        TransactionInstruction ix = SospesoInstructions.updateMeta(
                SPONSOR, pool, "Napoli v2", "", "", 0);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("update_meta"), head8(ix.getData()));
        BorshReader r = new BorshReader(ix.getData()).skip(8);
        assertEquals("Napoli v2", r.string());
        assertEquals("", r.string());
        assertEquals("", r.string());
        assertEquals(0, r.u8());

        List<AccountMeta> keys = ix.getKeys();
        // no system program on update
        assertEquals(3, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isWritable());
        assertEquals(Pda.meta(pool).getAddress(), keys.get(2).getPublicKey());
        assertTrue(keys.get(2).isWritable());
    }

    @Test
    void clearMetaEncodesDiscriminatorOnly() {
        PublicKey pool = Pda.sospeso(SPONSOR, 3).getAddress();
        TransactionInstruction ix = SospesoInstructions.clearMeta(SPONSOR, pool);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("clear_meta"), ix.getData());
        assertEquals(8, ix.getData().length);

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(3, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isWritable());
        assertEquals(Pda.meta(pool).getAddress(), keys.get(2).getPublicKey());
        assertTrue(keys.get(2).isWritable());
    }

    @Test
    void extendExpiryEncodesTimestampAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 4).getAddress();
        TransactionInstruction ix = SospesoInstructions.extendExpiry(SPONSOR, pool, 2_000_000_000L);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("extend_expiry"), head8(ix.getData()));
        assertEquals(16, ix.getData().length); // 8 + i64
        assertEquals(2_000_000_000L, new BorshReader(ix.getData()).skip(8).i64());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(2, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
    }

    @Test
    void initRegistryEncodesDiscriminatorAndAccounts() {
        TransactionInstruction ix = SospesoInstructions.initRegistry(PAYER);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("init_registry"), ix.getData());
        assertEquals(8, ix.getData().length);

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(3, keys.size());
        assertEquals(PAYER, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(Pda.registry().getAddress(), keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(2).getPublicKey());
        assertFalse(keys.get(2).isWritable());
    }

    @Test
    void registerPoolEncodesDiscriminatorAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 6).getAddress();
        TransactionInstruction ix = SospesoInstructions.registerPool(PAYER, pool);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("register_pool"), ix.getData());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(5, keys.size());
        assertEquals(PAYER, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertEquals(Pda.registry().getAddress(), keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
        // pool: readonly
        assertEquals(pool, keys.get(2).getPublicKey());
        assertFalse(keys.get(2).isWritable());
        assertEquals(Pda.registryEntry(pool).getAddress(), keys.get(3).getPublicKey());
        assertTrue(keys.get(3).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(4).getPublicKey());
        assertFalse(keys.get(4).isWritable());
    }

    @Test
    void syncPoolStatsEncodesDiscriminatorAndAccounts() {
        PublicKey pool = Pda.sospeso(SPONSOR, 6).getAddress();
        TransactionInstruction ix = SospesoInstructions.syncPoolStats(pool);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("sync_pool_stats"), ix.getData());

        List<AccountMeta> keys = ix.getKeys();
        // no signer, no system program
        assertEquals(3, keys.size());
        assertEquals(Pda.registry().getAddress(), keys.get(0).getPublicKey());
        assertFalse(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(pool, keys.get(1).getPublicKey());
        assertFalse(keys.get(1).isWritable());
        assertEquals(Pda.registryEntry(pool).getAddress(), keys.get(2).getPublicKey());
        assertTrue(keys.get(2).isWritable());
    }

    @Test
    void emitVersionEncodesDiscriminatorAndSigner() {
        TransactionInstruction ix = SospesoInstructions.emitVersion(PAYER);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("emit_version"), ix.getData());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(1, keys.size());
        // caller: signer, readonly
        assertEquals(PAYER, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertFalse(keys.get(0).isWritable());
    }

    @Test
    void registerBridgeEncodesFixedArraysAndAccounts() {
        TransactionInstruction ix = SospesoInstructions.registerBridge(
                SPONSOR, "jvm", "https://gaslt-jvm.up.railway.app", "gaslt-java");

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("register_bridge"), head8(ix.getData()));
        // 8 disc + 16 + 160 + 48 fixed arrays
        assertEquals(8 + 16 + 160 + 48, ix.getData().length);

        // The fixed arrays decode back as NUL-trimmed ascii.
        BorshReader r = new BorshReader(ix.getData()).skip(8);
        assertEquals("jvm", r.fixedAscii(16));
        assertEquals("https://gaslt-jvm.up.railway.app", r.fixedAscii(160));
        assertEquals("gaslt-java", r.fixedAscii(48));

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(3, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(Pda.bridge(SPONSOR).getAddress(), keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
        assertEquals(SospesoProgram.SYSTEM_PROGRAM_ID, keys.get(2).getPublicKey());
        assertFalse(keys.get(2).isWritable());
    }

    @Test
    void updateBridgeEncodesFixedArraysAndFlag() {
        TransactionInstruction ix = SospesoInstructions.updateBridge(
                SPONSOR, "https://new.example.com", "label", false);

        assertArrayEquals(
                SospesoProgram.instructionDiscriminator("update_bridge"), head8(ix.getData()));
        // 8 disc + 160 + 48 + bool
        assertEquals(8 + 160 + 48 + 1, ix.getData().length);

        BorshReader r = new BorshReader(ix.getData()).skip(8);
        assertEquals("https://new.example.com", r.fixedAscii(160));
        assertEquals("label", r.fixedAscii(48));
        assertFalse(r.bool());

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(2, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertEquals(Pda.bridge(SPONSOR).getAddress(), keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
    }

    @Test
    void revokeBridgeEncodesDiscriminatorOnly() {
        TransactionInstruction ix = SospesoInstructions.revokeBridge(SPONSOR);

        assertArrayEquals(SospesoProgram.instructionDiscriminator("revoke_bridge"), ix.getData());
        assertEquals(8, ix.getData().length);

        List<AccountMeta> keys = ix.getKeys();
        assertEquals(2, keys.size());
        assertEquals(SPONSOR, keys.get(0).getPublicKey());
        assertTrue(keys.get(0).isSigner());
        assertTrue(keys.get(0).isWritable());
        assertEquals(Pda.bridge(SPONSOR).getAddress(), keys.get(1).getPublicKey());
        assertTrue(keys.get(1).isWritable());
    }
}
