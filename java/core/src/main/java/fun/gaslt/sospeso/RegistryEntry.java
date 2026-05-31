package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code RegistryEntry} account (program v0.1.3): the registry's
 * record of a single pool, holding the last-synced snapshot of that pool's
 * lamports and claim count.
 */
public final class RegistryEntry {

    /** Fixed account size: 8 disc + 32 + 32 + 8 + 4 + 8 + 8 + 1. */
    public static final int ACCOUNT_SIZE = 101;

    private final PublicKey sospeso;
    private final PublicKey registry;
    private final long recordedLamports;
    private final long recordedClaims;
    private final long registeredTs;
    private final long syncedTs;
    private final int bump;

    RegistryEntry(
            PublicKey sospeso,
            PublicKey registry,
            long recordedLamports,
            long recordedClaims,
            long registeredTs,
            long syncedTs,
            int bump) {
        this.sospeso = sospeso;
        this.registry = registry;
        this.recordedLamports = recordedLamports;
        this.recordedClaims = recordedClaims;
        this.registeredTs = registeredTs;
        this.syncedTs = syncedTs;
        this.bump = bump;
    }

    /**
     * Decode a registry entry from raw account data including the leading 8-byte
     * Anchor discriminator (validated against {@code sha256("account:RegistryEntry")}).
     */
    public static RegistryEntry from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("RegistryEntry");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a RegistryEntry account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey sospeso = r.pubkey();
        PublicKey registry = r.pubkey();
        long recordedLamports = r.u64();
        long recordedClaims = r.u32();
        long registeredTs = r.i64();
        long syncedTs = r.i64();
        int bump = r.u8();
        return new RegistryEntry(
                sospeso, registry, recordedLamports, recordedClaims, registeredTs, syncedTs, bump);
    }

    public PublicKey sospeso() {
        return sospeso;
    }

    public PublicKey registry() {
        return registry;
    }

    public long recordedLamports() {
        return recordedLamports;
    }

    public long recordedClaims() {
        return recordedClaims;
    }

    public long registeredTs() {
        return registeredTs;
    }

    public long syncedTs() {
        return syncedTs;
    }

    public int bump() {
        return bump;
    }
}
