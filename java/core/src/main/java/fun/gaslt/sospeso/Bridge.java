package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code Bridge} account (program v0.1.3), advertising an
 * off-chain service that integrates with the sospeso program. A bridge is a PDA
 * at {@code ["bridge", authority]} that the authority registers and controls.
 *
 * <p>The {@code kind}, {@code endpoint}, and {@code label} fields are fixed-size
 * null-padded ascii arrays on chain ({@code [u8; 16]}, {@code [u8; 160]},
 * {@code [u8; 48]}); they are decoded here as trimmed strings.
 */
public final class Bridge {

    /** Fixed account size: 8 disc + 32 + 16 + 160 + 48 + 1 + 8 + 8 + 1. */
    public static final int ACCOUNT_SIZE = 282;

    private final PublicKey authority;
    private final String kind;
    private final String endpoint;
    private final String label;
    private final boolean enabled;
    private final long registeredAt;
    private final long updatedAt;
    private final int bump;

    Bridge(
            PublicKey authority,
            String kind,
            String endpoint,
            String label,
            boolean enabled,
            long registeredAt,
            long updatedAt,
            int bump) {
        this.authority = authority;
        this.kind = kind;
        this.endpoint = endpoint;
        this.label = label;
        this.enabled = enabled;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
        this.bump = bump;
    }

    /**
     * Decode a bridge account from raw account data including the leading 8-byte
     * Anchor discriminator (validated against {@code sha256("account:Bridge")}).
     */
    public static Bridge from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("Bridge");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a Bridge account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey authority = r.pubkey();
        String kind = r.fixedAscii(16);
        String endpoint = r.fixedAscii(160);
        String label = r.fixedAscii(48);
        boolean enabled = r.bool();
        long registeredAt = r.i64();
        long updatedAt = r.i64();
        int bump = r.u8();
        return new Bridge(authority, kind, endpoint, label, enabled, registeredAt, updatedAt, bump);
    }

    public PublicKey authority() {
        return authority;
    }

    public String kind() {
        return kind;
    }

    public String endpoint() {
        return endpoint;
    }

    public String label() {
        return label;
    }

    public boolean enabled() {
        return enabled;
    }

    public long registeredAt() {
        return registeredAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public int bump() {
        return bump;
    }
}
