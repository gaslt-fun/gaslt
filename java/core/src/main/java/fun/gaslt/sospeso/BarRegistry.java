package fun.gaslt.sospeso;

import java.math.BigInteger;
import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code BarRegistry} account (program v0.1.3): the singleton
 * PDA at {@code ["registry"]} that aggregates protocol-wide counters across all
 * registered pools.
 */
public final class BarRegistry {

    /** Fixed account size: 8 disc + 32 + 8 + 16 + 8 + 8 + 8 + 1. */
    public static final int ACCOUNT_SIZE = 89;

    private final PublicKey authority;
    private final long totalPools;
    private final BigInteger totalSuspendedLamports;
    private final long totalClaims;
    private final long createdTs;
    private final long lastUpdateTs;
    private final int bump;

    BarRegistry(
            PublicKey authority,
            long totalPools,
            BigInteger totalSuspendedLamports,
            long totalClaims,
            long createdTs,
            long lastUpdateTs,
            int bump) {
        this.authority = authority;
        this.totalPools = totalPools;
        this.totalSuspendedLamports = totalSuspendedLamports;
        this.totalClaims = totalClaims;
        this.createdTs = createdTs;
        this.lastUpdateTs = lastUpdateTs;
        this.bump = bump;
    }

    /**
     * Decode the registry account from raw account data including the leading
     * 8-byte Anchor discriminator (validated against
     * {@code sha256("account:BarRegistry")}).
     */
    public static BarRegistry from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("BarRegistry");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a BarRegistry account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey authority = r.pubkey();
        long totalPools = r.u64();
        BigInteger totalSuspendedLamports = r.u128();
        long totalClaims = r.u64();
        long createdTs = r.i64();
        long lastUpdateTs = r.i64();
        int bump = r.u8();
        return new BarRegistry(
                authority, totalPools, totalSuspendedLamports, totalClaims, createdTs, lastUpdateTs, bump);
    }

    public PublicKey authority() {
        return authority;
    }

    public long totalPools() {
        return totalPools;
    }

    public BigInteger totalSuspendedLamports() {
        return totalSuspendedLamports;
    }

    public long totalClaims() {
        return totalClaims;
    }

    public long createdTs() {
        return createdTs;
    }

    public long lastUpdateTs() {
        return lastUpdateTs;
    }

    public int bump() {
        return bump;
    }
}
