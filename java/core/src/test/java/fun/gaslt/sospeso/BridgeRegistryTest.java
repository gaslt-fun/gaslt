package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

/**
 * Covers the v0.1.3 registry and bridge accounts. Discriminators are pinned to
 * the exact values published in the program IDL ({@code sha256("account:<Name>")[..8]}).
 */
class BridgeRegistryTest {

    private static final PublicKey AUTHORITY =
            new PublicKey("So11111111111111111111111111111111111111112");
    private static final PublicKey POOL =
            new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    @Test
    void accountDiscriminatorsMatchIdl() {
        // Values copied from anchor/target/idl/sospeso_verifier.json.
        assertEquals("e7e81f626e03173b", hex(SospesoProgram.accountDiscriminator("Bridge")));
        assertEquals("8510c7d2a86240c5", hex(SospesoProgram.accountDiscriminator("BarRegistry")));
        assertEquals("30c6f0fc9bba4810", hex(SospesoProgram.accountDiscriminator("RegistryEntry")));
        assertEquals("605af320d0109891", hex(SospesoProgram.accountDiscriminator("SospesoMeta")));
    }

    @Test
    void pdaSeedsMatchProgram() {
        // registry singleton: ["registry"]
        assertEquals(
                PublicKey.findProgramAddress(List.of("registry".getBytes()), SospesoProgram.PROGRAM_ID)
                        .getAddress(),
                Pda.registry().getAddress());
        // bridge: ["bridge", authority]
        assertEquals(
                PublicKey.findProgramAddress(
                        List.of("bridge".getBytes(), AUTHORITY.toByteArray()), SospesoProgram.PROGRAM_ID)
                        .getAddress(),
                Pda.bridge(AUTHORITY).getAddress());
        // registry entry: ["regentry", pool]
        assertEquals(
                PublicKey.findProgramAddress(
                        List.of("regentry".getBytes(), POOL.toByteArray()), SospesoProgram.PROGRAM_ID)
                        .getAddress(),
                Pda.registryEntry(POOL).getAddress());
        // meta: ["meta", pool]
        assertEquals(
                PublicKey.findProgramAddress(
                        List.of("meta".getBytes(), POOL.toByteArray()), SospesoProgram.PROGRAM_ID)
                        .getAddress(),
                Pda.meta(POOL).getAddress());
        assertNotEquals(Pda.bridge(AUTHORITY).getAddress(), Pda.bridge(POOL).getAddress());
    }

    @Test
    void decodesBridge() {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("Bridge"))
                .pubkey(AUTHORITY)
                .bytes(asciiFixed("jvm", 16))
                .bytes(asciiFixed("https://gaslt-jvm.up.railway.app", 160))
                .bytes(asciiFixed("gaslt-java", 48))
                .bool(true)
                .i64(1_900_000_000L)
                .i64(1_900_000_500L)
                .u8(252)
                .toByteArray();
        assertEquals(Bridge.ACCOUNT_SIZE, data.length);

        Bridge b = Bridge.from(data);
        assertEquals(AUTHORITY, b.authority());
        assertEquals("jvm", b.kind());
        assertEquals("https://gaslt-jvm.up.railway.app", b.endpoint());
        assertEquals("gaslt-java", b.label());
        assertTrue(b.enabled());
        assertEquals(1_900_000_000L, b.registeredAt());
        assertEquals(1_900_000_500L, b.updatedAt());
        assertEquals(252, b.bump());
    }

    @Test
    void bridgeRejectsWrongDiscriminator() {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("BarRegistry")) // wrong type
                .pubkey(AUTHORITY)
                .bytes(new byte[16 + 160 + 48])
                .bool(false)
                .i64(0).i64(0).u8(0)
                .toByteArray();
        assertThrows(IllegalArgumentException.class, () -> Bridge.from(data));
    }

    @Test
    void decodesBarRegistryIncludingU128() {
        BigInteger suspended = new BigInteger("123456789012345678901234567890");
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("BarRegistry"))
                .pubkey(AUTHORITY)
                .u64(7)
                .u128(suspended)
                .u64(42)
                .i64(1_800_000_000L)
                .i64(1_900_000_000L)
                .u8(255)
                .toByteArray();
        assertEquals(BarRegistry.ACCOUNT_SIZE, data.length);

        BarRegistry reg = BarRegistry.from(data);
        assertEquals(AUTHORITY, reg.authority());
        assertEquals(7, reg.totalPools());
        assertEquals(suspended, reg.totalSuspendedLamports());
        assertEquals(42, reg.totalClaims());
        assertEquals(1_800_000_000L, reg.createdTs());
        assertEquals(255, reg.bump());
    }

    @Test
    void decodesRegistryEntry() {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("RegistryEntry"))
                .pubkey(POOL)
                .pubkey(Pda.registry().getAddress())
                .u64(5_000_000L)
                .u32(9)
                .i64(1_800_000_000L)
                .i64(1_850_000_000L)
                .u8(254)
                .toByteArray();
        assertEquals(RegistryEntry.ACCOUNT_SIZE, data.length);

        RegistryEntry e = RegistryEntry.from(data);
        assertEquals(POOL, e.sospeso());
        assertEquals(5_000_000L, e.recordedLamports());
        assertEquals(9, e.recordedClaims());
        assertEquals(254, e.bump());
    }

    @Test
    void decodesSospesoMetaWithVariableStrings() {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.accountDiscriminator("SospesoMeta"))
                .pubkey(POOL)
                .pubkey(AUTHORITY)
                .string("Napoli onboarding")
                .string("Free first gas for new wallets")
                .string("https://gaslt.fun/p/1")
                .u8(2)
                .i64(1_800_000_000L)
                .i64(1_810_000_000L)
                .u8(253)
                .toByteArray();

        SospesoMeta m = SospesoMeta.from(data);
        assertEquals(POOL, m.sospeso());
        assertEquals(AUTHORITY, m.sponsor());
        assertEquals("Napoli onboarding", m.label());
        assertEquals("Free first gas for new wallets", m.description());
        assertEquals("https://gaslt.fun/p/1", m.uri());
        assertEquals(2, m.category());
        assertEquals(253, m.bump());
    }

    @Test
    void fixedAsciiTrimsAtNul() {
        byte[] data = new BorshWriter().bytes(asciiFixed("jvm", 16)).toByteArray();
        assertEquals("jvm", new BorshReader(data).fixedAscii(16));
        assertFalse(new BorshReader(asciiFixed("", 4)).fixedAscii(4).length() > 0);
    }

    /** Build a NUL-padded fixed-size ascii field. */
    private static byte[] asciiFixed(String s, int n) {
        byte[] out = new byte[n];
        byte[] src = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, out, 0, Math.min(src.length, n));
        return out;
    }
}
