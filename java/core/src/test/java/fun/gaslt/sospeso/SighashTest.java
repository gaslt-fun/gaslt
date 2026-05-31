package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/**
 * Anchor discriminators are the first 8 bytes of {@code sha256("<ns>:<name>")}.
 * The expected values here are computed independently (sha256 + truncate) and
 * pinned, so a regression in {@link SospesoProgram#sighash} fails loudly.
 */
class SighashTest {

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    @Test
    void instructionDiscriminatorsMatchKnownValues() {
        // All fifteen values are copied verbatim from the program IDL
        // (anchor/target/idl/sospeso_verifier.json) -> sha256("global:<ix>")[..8].
        assertEquals("0bca5fa00099a922",
                hex(SospesoProgram.instructionDiscriminator("create_sospeso")));
        assertEquals("ceaf285534e44a1f",
                hex(SospesoProgram.instructionDiscriminator("claim_sospeso")));
        assertEquals("ece160093c6a4dd0",
                hex(SospesoProgram.instructionDiscriminator("top_up")));
        assertEquals("2cb1ecf9916da3ba",
                hex(SospesoProgram.instructionDiscriminator("reclaim")));
        assertEquals("613afbef8d640651",
                hex(SospesoProgram.instructionDiscriminator("set_meta")));
        assertEquals("a069dfce8dfb0358",
                hex(SospesoProgram.instructionDiscriminator("update_meta")));
        assertEquals("e53accbe8af12db4",
                hex(SospesoProgram.instructionDiscriminator("clear_meta")));
        assertEquals("d8278d0fe664181a",
                hex(SospesoProgram.instructionDiscriminator("extend_expiry")));
        assertEquals("83160467185ea3ef",
                hex(SospesoProgram.instructionDiscriminator("init_registry")));
        assertEquals("55e5722f4b91a664",
                hex(SospesoProgram.instructionDiscriminator("register_pool")));
        assertEquals("cba76a236c0c5ab5",
                hex(SospesoProgram.instructionDiscriminator("sync_pool_stats")));
        assertEquals("f6ce2c874b2685c1",
                hex(SospesoProgram.instructionDiscriminator("emit_version")));
        assertEquals("6f8eebec46090585",
                hex(SospesoProgram.instructionDiscriminator("register_bridge")));
        assertEquals("3f54048fa1964ac3",
                hex(SospesoProgram.instructionDiscriminator("update_bridge")));
        assertEquals("dafb3679bddc0653",
                hex(SospesoProgram.instructionDiscriminator("revoke_bridge")));
    }

    @Test
    void accountDiscriminatorsMatchKnownValues() {
        assertEquals("8e666a37d7fa10fc",
                hex(SospesoProgram.accountDiscriminator("Sospeso")));
        assertEquals("dfe90be57ca5cf1c",
                hex(SospesoProgram.accountDiscriminator("ClaimReceipt")));
        assertEquals("605af320d0109891",
                hex(SospesoProgram.accountDiscriminator("SospesoMeta")));
        assertEquals("8510c7d2a86240c5",
                hex(SospesoProgram.accountDiscriminator("BarRegistry")));
        assertEquals("30c6f0fc9bba4810",
                hex(SospesoProgram.accountDiscriminator("RegistryEntry")));
        assertEquals("e7e81f626e03173b",
                hex(SospesoProgram.accountDiscriminator("Bridge")));
    }

    @Test
    void discriminatorsAreEightBytes() {
        assertEquals(8, SospesoProgram.instructionDiscriminator("create_sospeso").length);
        assertEquals(8, SospesoProgram.accountDiscriminator("Sospeso").length);
    }
}
