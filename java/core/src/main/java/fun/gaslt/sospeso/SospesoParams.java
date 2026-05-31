package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * Parameters a sponsor supplies when opening a sospeso, matching the
 * {@code create_sospeso} arguments of program v0.1.3 (the upgraded program no
 * longer binds a pool to a specific target program).
 *
 * <p>Instances are immutable; the {@code with*} methods return a modified copy.
 */
public final class SospesoParams {

    private final PublicKey sponsor;
    private final long lamportsTotal;
    private final long maxPerClaim;
    private final long maxClaims;
    private final long expiryTs;
    private final boolean newWalletOnly;

    private SospesoParams(
            PublicKey sponsor,
            long lamportsTotal,
            long maxPerClaim,
            long maxClaims,
            long expiryTs,
            boolean newWalletOnly) {
        this.sponsor = Objects.requireNonNull(sponsor, "sponsor");
        this.lamportsTotal = lamportsTotal;
        this.maxPerClaim = maxPerClaim;
        this.maxClaims = maxClaims;
        this.expiryTs = expiryTs;
        this.newWalletOnly = newWalletOnly;
    }

    /**
     * Start a parameter set with the required fields; the rest default to "open"
     * (no per-claim cap, no claim count cap, never expires, any wallet).
     */
    public static SospesoParams of(PublicKey sponsor, long lamportsTotal) {
        return new SospesoParams(sponsor, lamportsTotal, 0, 0, 0, false);
    }

    /** Set the per-claim lamport ceiling (0 = no cap). */
    public SospesoParams withMaxPerClaim(long cap) {
        return new SospesoParams(sponsor, lamportsTotal, cap, maxClaims, expiryTs, newWalletOnly);
    }

    /** Set the total claim count limit (0 = unbounded). */
    public SospesoParams withMaxClaims(long max) {
        return new SospesoParams(sponsor, lamportsTotal, maxPerClaim, max, expiryTs, newWalletOnly);
    }

    /** Set the unix expiry timestamp (0 = never). */
    public SospesoParams withExpiry(long ts) {
        return new SospesoParams(sponsor, lamportsTotal, maxPerClaim, maxClaims, ts, newWalletOnly);
    }

    /** Mark the pool as claimable only by new wallets. */
    public SospesoParams newWalletsOnly() {
        return new SospesoParams(sponsor, lamportsTotal, maxPerClaim, maxClaims, expiryTs, true);
    }

    public PublicKey sponsor() {
        return sponsor;
    }

    public long lamportsTotal() {
        return lamportsTotal;
    }

    public long maxPerClaim() {
        return maxPerClaim;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SospesoParams p)) {
            return false;
        }
        return lamportsTotal == p.lamportsTotal
                && maxPerClaim == p.maxPerClaim
                && maxClaims == p.maxClaims
                && expiryTs == p.expiryTs
                && newWalletOnly == p.newWalletOnly
                && sponsor.equals(p.sponsor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sponsor, lamportsTotal, maxPerClaim, maxClaims, expiryTs, newWalletOnly);
    }
}
