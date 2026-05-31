package fun.gaslt.sospeso;

import java.math.BigInteger;
import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A decoded on-chain {@code Sospeso} pool account, mirroring the field layout of
 * the Rust {@code state::Sospeso} struct exactly as published in the program IDL.
 * The convenience predicates ({@link #isExpired}, {@link #claimsExhausted},
 * {@link #canCover}) and {@link #utilizationBps} match the {@code gaslt-core}
 * behaviour so off-chain callers reason about a pool exactly as the program does.
 *
 * <p>On-chain byte layout (after the 8-byte account discriminator):
 * {@code sponsor(32) lamports_total(8) lamports_remaining(8) max_per_claim(8)
 * claims_count(4) max_claims(4) expiry_ts(8) new_wallet_only(1) nonce(8) bump(1)}.
 */
public final class Sospeso {

    /** Expected total account size in bytes: 8-byte discriminator + 82-byte body. */
    public static final int ACCOUNT_SIZE = 90;

    private final PublicKey sponsor;
    private final long lamportsTotal;
    private final long lamportsRemaining;
    private final long maxPerClaim;
    private final long claimsCount;
    private final long maxClaims;
    private final long expiryTs;
    private final boolean newWalletOnly;
    private final long nonce;
    private final int bump;

    Sospeso(
            PublicKey sponsor,
            long lamportsTotal,
            long lamportsRemaining,
            long maxPerClaim,
            long claimsCount,
            long maxClaims,
            long expiryTs,
            boolean newWalletOnly,
            long nonce,
            int bump) {
        this.sponsor = sponsor;
        this.lamportsTotal = lamportsTotal;
        this.lamportsRemaining = lamportsRemaining;
        this.maxPerClaim = maxPerClaim;
        this.claimsCount = claimsCount;
        this.maxClaims = maxClaims;
        this.expiryTs = expiryTs;
        this.newWalletOnly = newWalletOnly;
        this.nonce = nonce;
        this.bump = bump;
    }

    /**
     * Decode a pool account from its raw account data, including the leading
     * 8-byte Anchor account discriminator (which is validated against
     * {@code sha256("account:Sospeso")}).
     *
     * @throws IllegalArgumentException if the discriminator does not match or the
     *     buffer is the wrong length
     */
    public static Sospeso from(byte[] accountData) {
        Objects.requireNonNull(accountData, "accountData");
        byte[] expected = SospesoProgram.accountDiscriminator("Sospeso");
        for (int i = 0; i < 8; i++) {
            if (accountData[i] != expected[i]) {
                throw new IllegalArgumentException("account discriminator mismatch: not a Sospeso account");
            }
        }
        BorshReader r = new BorshReader(accountData).skip(8);
        PublicKey sponsor = r.pubkey();
        long lamportsTotal = r.u64();
        long lamportsRemaining = r.u64();
        long maxPerClaim = r.u64();
        long claimsCount = r.u32();
        long maxClaims = r.u32();
        long expiryTs = r.i64();
        boolean newWalletOnly = r.bool();
        long nonce = r.u64();
        int bump = r.u8();
        return new Sospeso(
                sponsor, lamportsTotal, lamportsRemaining, maxPerClaim,
                claimsCount, maxClaims, expiryTs, newWalletOnly, nonce, bump);
    }

    /** Whether the pool has expired at {@code now} (expiry 0 means never). */
    public boolean isExpired(long now) {
        return expiryTs != 0 && expiryTs <= now;
    }

    /** Whether the pool has served its maximum number of claims (0 = unbounded). */
    public boolean claimsExhausted() {
        return maxClaims != 0 && claimsCount >= maxClaims;
    }

    /** Whether the pool can still cover a claim of {@code amount} lamports at {@code now}. */
    public boolean canCover(long amount, long now) {
        return amount > 0
                && !isExpired(now)
                && !claimsExhausted()
                && lamportsRemaining >= amount
                && (maxPerClaim == 0 || amount <= maxPerClaim);
    }

    /** Fraction of the original budget already spent, in basis points (0-10000). */
    public int utilizationBps() {
        if (lamportsTotal == 0) {
            return 0;
        }
        long spent = lamportsTotal - lamportsRemaining;
        // Widen to 128-bit (as the Rust does with u128) so spent * 10_000 cannot
        // overflow even for very large pools.
        BigInteger bps = BigInteger.valueOf(spent)
                .multiply(BigInteger.valueOf(10_000L))
                .divide(BigInteger.valueOf(lamportsTotal));
        return bps.intValueExact();
    }

    public PublicKey sponsor() {
        return sponsor;
    }

    public long lamportsTotal() {
        return lamportsTotal;
    }

    public long lamportsRemaining() {
        return lamportsRemaining;
    }

    public long maxPerClaim() {
        return maxPerClaim;
    }

    public long claimsCount() {
        return claimsCount;
    }

    public long maxClaims() {
        return maxClaims;
    }

    public long expiryTs() {
        return expiryTs;
    }

    public boolean newWalletOnly() {
        return newWalletOnly;
    }

    public long nonce() {
        return nonce;
    }

    public int bump() {
        return bump;
    }
}
