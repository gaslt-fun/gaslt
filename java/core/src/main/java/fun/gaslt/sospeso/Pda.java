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
 *   <li>Bar registry: {@code ["registry"]} (singleton)</li>
 *   <li>Bridge: {@code ["bridge", authority]}</li>
 *   <li>Bridge pulse: {@code ["pulse", bridge]}</li>
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

    /** Seed prefix for the singleton bar registry PDA. */
    public static final byte[] REGISTRY_SEED = "registry".getBytes(StandardCharsets.UTF_8);

    /** Seed prefix for a bridge PDA. */
    public static final byte[] BRIDGE_SEED = "bridge".getBytes(StandardCharsets.UTF_8);

    /** Seed prefix for a registry entry PDA. */
    public static final byte[] REGENTRY_SEED = "regentry".getBytes(StandardCharsets.UTF_8);

    /** Seed prefix for a sospeso meta PDA. */
    public static final byte[] META_SEED = "meta".getBytes(StandardCharsets.UTF_8);

    /** Seed prefix for a bridge pulse PDA. */
    public static final byte[] PULSE_SEED = "pulse".getBytes(StandardCharsets.UTF_8);

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

    /** Derive the singleton bar registry PDA ({@code ["registry"]}). */
    public static PublicKey.ProgramDerivedAddress registry() {
        return PublicKey.findProgramAddress(
                List.of(REGISTRY_SEED), SospesoProgram.PROGRAM_ID);
    }

    /** Derive the bridge PDA for {@code authority} ({@code ["bridge", authority]}). */
    public static PublicKey.ProgramDerivedAddress bridge(PublicKey authority) {
        return PublicKey.findProgramAddress(
                List.of(BRIDGE_SEED, authority.toByteArray()),
                SospesoProgram.PROGRAM_ID);
    }

    /** Derive the registry entry PDA for {@code pool} ({@code ["regentry", sospeso]}). */
    public static PublicKey.ProgramDerivedAddress registryEntry(PublicKey pool) {
        return PublicKey.findProgramAddress(
                List.of(REGENTRY_SEED, pool.toByteArray()),
                SospesoProgram.PROGRAM_ID);
    }

    /** Derive the meta PDA for {@code pool} ({@code ["meta", sospeso]}). */
    public static PublicKey.ProgramDerivedAddress meta(PublicKey pool) {
        return PublicKey.findProgramAddress(
                List.of(META_SEED, pool.toByteArray()),
                SospesoProgram.PROGRAM_ID);
    }

    /** Derive the bridge pulse PDA for {@code bridge} ({@code ["pulse", bridge]}). */
    public static PublicKey.ProgramDerivedAddress bridgePulse(PublicKey bridge) {
        return PublicKey.findProgramAddress(
                List.of(PULSE_SEED, bridge.toByteArray()),
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
