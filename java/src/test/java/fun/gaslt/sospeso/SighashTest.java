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
        assertEquals("0bca5fa00099a922",
                hex(SospesoProgram.instructionDiscriminator("create_sospeso")));
        assertEquals("ceaf285534e44a1f",
                hex(SospesoProgram.instructionDiscriminator("claim_sospeso")));
        assertEquals("ece160093c6a4dd0",
                hex(SospesoProgram.instructionDiscriminator("top_up")));
        assertEquals("2cb1ecf9916da3ba",
                hex(SospesoProgram.instructionDiscriminator("reclaim")));
    }

    @Test
    void accountDiscriminatorsMatchKnownValues() {
        assertEquals("8e666a37d7fa10fc",
                hex(SospesoProgram.accountDiscriminator("Sospeso")));
        assertEquals("dfe90be57ca5cf1c",
                hex(SospesoProgram.accountDiscriminator("ClaimReceipt")));
    }

    @Test
    void discriminatorsAreEightBytes() {
        assertEquals(8, SospesoProgram.instructionDiscriminator("create_sospeso").length);
        assertEquals(8, SospesoProgram.accountDiscriminator("Sospeso").length);
    }
}
