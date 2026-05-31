package fun.gaslt.sospeso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.p2p.solanaj.core.PublicKey;

/**
 * Program-level constants and Anchor discriminator helpers for the
 * {@code sospeso_verifier} program (mainnet, v0.1.3).
 *
 * <p>Anchor prefixes every instruction's data with an 8-byte discriminator equal
 * to the first eight bytes of {@code sha256("global:<instruction_name>")}, and
 * every account's data with the first eight bytes of
 * {@code sha256("account:<AccountName>")}. Both are computed here rather than
 * hard-coded so they stay correct by construction.
 */
public final class SospesoProgram {

    /** The deployed {@code sospeso_verifier} program id on Solana mainnet. */
    public static final PublicKey PROGRAM_ID =
            new PublicKey("44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW");

    /** The System Program, used as the "any program" target sentinel. */
    public static final PublicKey SYSTEM_PROGRAM_ID =
            new PublicKey("11111111111111111111111111111111");

    private SospesoProgram() {
    }

    /** The 8-byte Anchor discriminator for an instruction named {@code name}. */
    public static byte[] instructionDiscriminator(String name) {
        return sighash("global", name);
    }

    /** The 8-byte Anchor discriminator for an account struct named {@code name}. */
    public static byte[] accountDiscriminator(String name) {
        return sighash("account", name);
    }

    /**
     * First eight bytes of {@code sha256("<namespace>:<name>")} — the Anchor
     * sighash construction shared by instruction and account discriminators.
     */
    static byte[] sighash(String namespace, String name) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest((namespace + ":" + name).getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOfRange(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
