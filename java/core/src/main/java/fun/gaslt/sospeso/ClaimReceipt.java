package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code ClaimReceipt} account, mirroring the Rust
 * {@code state::ClaimReceipt} struct. The presence of this account for a
 * {@code (sospeso, beneficiary)} pair is the program's double-claim guard.
 */
public final class ClaimReceipt {

    /** Expected total account size in bytes: 8-byte discriminator + 49-byte body. */
    public static final int ACCOUNT_SIZE = 57;

    private final PublicKey beneficiary;
    private final long amount;
    private final long ts;
    private final int bump;

    ClaimReceipt(PublicKey beneficiary, long amount, long ts, int bump) {
        this.beneficiary = beneficiary;
        this.amount = amount;
        this.ts = ts;
        this.bump = bump;
    }

    /**
     * Decode a receipt account from raw account data including the leading
     * 8-byte Anchor account discriminator (validated against
     * {@code sha256("account:ClaimReceipt")}).
     */
    public static ClaimReceipt from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("ClaimReceipt");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a ClaimReceipt account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey beneficiary = r.pubkey();
        long amount = r.u64();
        long ts = r.i64();
        int bump = r.u8();
        return new ClaimReceipt(beneficiary, amount, ts, bump);
    }

    public PublicKey beneficiary() {
        return beneficiary;
    }

    public long amount() {
        return amount;
    }

    public long ts() {
        return ts;
    }

    public int bump() {
        return bump;
    }
}
