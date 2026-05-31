package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code SospesoMeta} account (program v0.1.3): optional
 * human-facing metadata a sponsor attaches to a pool. Its {@code label},
 * {@code description}, and {@code uri} are variable-length Borsh strings, so this
 * account has no fixed size.
 */
public final class SospesoMeta {

    private final PublicKey sospeso;
    private final PublicKey sponsor;
    private final String label;
    private final String description;
    private final String uri;
    private final int category;
    private final long createdTs;
    private final long updatedTs;
    private final int bump;

    SospesoMeta(
            PublicKey sospeso,
            PublicKey sponsor,
            String label,
            String description,
            String uri,
            int category,
            long createdTs,
            long updatedTs,
            int bump) {
        this.sospeso = sospeso;
        this.sponsor = sponsor;
        this.label = label;
        this.description = description;
        this.uri = uri;
        this.category = category;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
        this.bump = bump;
    }

    /**
     * Decode the meta account from raw account data including the leading 8-byte
     * Anchor discriminator (validated against {@code sha256("account:SospesoMeta")}).
     */
    public static SospesoMeta from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("SospesoMeta");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a SospesoMeta account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey sospeso = r.pubkey();
        PublicKey sponsor = r.pubkey();
        String label = r.string();
        String description = r.string();
        String uri = r.string();
        int category = r.u8();
        long createdTs = r.i64();
        long updatedTs = r.i64();
        int bump = r.u8();
        return new SospesoMeta(
                sospeso, sponsor, label, description, uri, category, createdTs, updatedTs, bump);
    }

    public PublicKey sospeso() {
        return sospeso;
    }

    public PublicKey sponsor() {
        return sponsor;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String uri() {
        return uri;
    }

    public int category() {
        return category;
    }

    public long createdTs() {
        return createdTs;
    }

    public long updatedTs() {
        return updatedTs;
    }

    public int bump() {
        return bump;
    }
}
