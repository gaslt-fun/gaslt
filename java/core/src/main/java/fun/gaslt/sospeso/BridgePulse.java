package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code BridgePulse} account (program v0.1.4), the liveness
 * and work oracle for a registered {@link Bridge}. The bridge's off-chain
 * service (e.g. the JVM relayer) stamps this account on a schedule so anyone can
 * verify, from the chain alone, that the bridge is operating and how many claims
 * it has relayed.
 *
 * <p>The pulse is a PDA at {@code ["pulse", bridge]} (one per bridge), created on
 * the first {@code bridge_pulse} call and updated on every subsequent one. Only
 * the bridge authority may write it.
 *
 * <p>Its layout is fixed-width with no length-prefixed fields, so it decodes at
 * constant byte offsets:
 *
 * <pre>
 *   offset  size  field
 *   0       8     anchor discriminator
 *   8       32    bridge          (Pubkey)
 *   40      32    authority       (Pubkey)
 *   72      8     last_ts         (i64, little-endian)
 *   80      8     pulse_count     (u64, little-endian)
 *   88      8     relayed_claims  (u64, little-endian)
 *   96      4     version         (u32, little-endian)
 *   100     1     bump            (u8)
 *   ---     101   total account size
 * </pre>
 */
public final class BridgePulse {

    /** Fixed account size: 8 disc + 32 + 32 + 8 + 8 + 8 + 4 + 1. */
    public static final int ACCOUNT_SIZE = 101;

    private final PublicKey bridge;
    private final PublicKey authority;
    private final long lastTs;
    private final long pulseCount;
    private final long relayedClaims;
    private final long version;
    private final int bump;

    BridgePulse(
            PublicKey bridge,
            PublicKey authority,
            long lastTs,
            long pulseCount,
            long relayedClaims,
            long version,
            int bump) {
        this.bridge = bridge;
        this.authority = authority;
        this.lastTs = lastTs;
        this.pulseCount = pulseCount;
        this.relayedClaims = relayedClaims;
        this.version = version;
        this.bump = bump;
    }

    /**
     * Decode a pulse account from raw account data including the leading 8-byte
     * Anchor discriminator (validated against {@code sha256("account:BridgePulse")}).
     */
    public static BridgePulse from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("BridgePulse");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a BridgePulse account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey bridge = r.pubkey();
        PublicKey authority = r.pubkey();
        long lastTs = r.i64();
        long pulseCount = r.u64();
        long relayedClaims = r.u64();
        long version = r.u32();
        int bump = r.u8();
        return new BridgePulse(bridge, authority, lastTs, pulseCount, relayedClaims, version, bump);
    }

    /** The bridge this pulse belongs to. */
    public PublicKey bridge() {
        return bridge;
    }

    /** The bridge authority that stamps the pulse. */
    public PublicKey authority() {
        return authority;
    }

    /** Unix timestamp of the most recent pulse. */
    public long lastTs() {
        return lastTs;
    }

    /** Total number of pulses recorded since the account was created. */
    public long pulseCount() {
        return pulseCount;
    }

    /** Cumulative number of claims the bridge has reported relaying. */
    public long relayedClaims() {
        return relayedClaims;
    }

    /** Service-reported version stamped on the latest pulse. */
    public long version() {
        return version;
    }

    /** PDA bump. */
    public int bump() {
        return bump;
    }
}
