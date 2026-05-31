package fun.gaslt.sospeso;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.p2p.solanaj.core.PublicKey;

/**
 * Program-derived address (PDA) helpers for the sospeso program, mirroring the
 * on-chain seed layout exactly.
 *
 * <ul>
 *   <li>Pool: {@code ["sospeso", sponsor, nonce_le(8)]}</li>
 *   <li>Claim receipt: {@code ["claim", sospeso, beneficiary]}</li>
 * </ul>
 *
 * <p>A {@code nonce} is encoded as a little-endian {@code u64}, matching
 * {@code nonce.to_le_bytes()} in the Anchor account constraints.
 */
public final class Pda {

    /** Seed prefix for the pool PDA (matches {@code Sospeso::SEED}). */
    public static final byte[] SOSPESO_SEED = "sospeso".getBytes(StandardCharsets.UTF_8);

    /** Seed prefix for the claim receipt PDA (matches {@code ClaimReceipt::SEED}). */
    public static final byte[] CLAIM_SEED = "claim".getBytes(StandardCharsets.UTF_8);

    private Pda() {
    }

    /** Derive the pool PDA for {@code (sponsor, nonce)}. */
    public static PublicKey.ProgramDerivedAddress sospeso(PublicKey sponsor, long nonce) {
        return PublicKey.findProgramAddress(
                List.of(SOSPESO_SEED, sponsor.toByteArray(), nonceLeBytes(nonce)),
                SospesoProgram.PROGRAM_ID);
    }

    /** Derive the claim receipt PDA for {@code (sospeso, beneficiary)}. */
    public static PublicKey.ProgramDerivedAddress claimReceipt(PublicKey sospeso, PublicKey beneficiary) {
        return PublicKey.findProgramAddress(
                List.of(CLAIM_SEED, sospeso.toByteArray(), beneficiary.toByteArray()),
                SospesoProgram.PROGRAM_ID);
    }

    /** Little-endian 8-byte encoding of a {@code u64} nonce. */
    public static byte[] nonceLeBytes(long nonce) {
        byte[] le = new byte[8];
        for (int i = 0; i < 8; i++) {
            le[i] = (byte) ((nonce >>> (8 * i)) & 0xff);
        }
        return le;
    }
}
