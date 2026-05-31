package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * Parameters a sponsor supplies when opening a sospeso, mirroring
 * {@code SospesoParams} in the Rust {@code gaslt-core} crate (including its
 * builder-style setters and "open by default" semantics).
 *
 * <p>Instances are immutable; the {@code with*} methods return a modified copy.
 */
public final class SospesoParams {

    private final PublicKey sponsor;
    private final PublicKey program;
    private final long lamportsTotal;
    private final long maxPerClaim;
    private final long maxClaims;
    private final long expiryTs;
    private final boolean newWalletOnly;

    private SospesoParams(
            PublicKey sponsor,
            PublicKey program,
            long lamportsTotal,
            long maxPerClaim,
            long maxClaims,
            long expiryTs,
            boolean newWalletOnly) {
        this.sponsor = Objects.requireNonNull(sponsor, "sponsor");
        this.program = Objects.requireNonNull(program, "program");
        this.lamportsTotal = lamportsTotal;
        this.maxPerClaim = maxPerClaim;
        this.maxClaims = maxClaims;
        this.expiryTs = expiryTs;
        this.newWalletOnly = newWalletOnly;
    }

    /**
     * Start a parameter set with the required fields; the rest default to "open"
     * (no per-claim cap, no claim count cap, never expires, any wallet, any
     * target program).
     */
    public static SospesoParams of(PublicKey sponsor, long lamportsTotal) {
        return new SospesoParams(
                sponsor, SospesoProgram.SYSTEM_PROGRAM_ID, lamportsTotal, 0, 0, 0, false);
    }

    /** Set the per-claim lamport ceiling (0 = no cap). */
    public SospesoParams withMaxPerClaim(long cap) {
        return new SospesoParams(sponsor, program, lamportsTotal, cap, maxClaims, expiryTs, newWalletOnly);
    }

    /** Set the total claim count limit (0 = unbounded). */
    public SospesoParams withMaxClaims(long max) {
        return new SospesoParams(sponsor, program, lamportsTotal, maxPerClaim, max, expiryTs, newWalletOnly);
    }

    /** Set the unix expiry timestamp (0 = never). */
    public SospesoParams withExpiry(long ts) {
        return new SospesoParams(sponsor, program, lamportsTotal, maxPerClaim, maxClaims, ts, newWalletOnly);
    }

    /** Restrict the pool to a specific target program. */
    public SospesoParams forProgram(PublicKey targetProgram) {
        return new SospesoParams(sponsor, targetProgram, lamportsTotal, maxPerClaim, maxClaims, expiryTs, newWalletOnly);
    }

    /** Mark the pool as claimable only by new wallets. */
    public SospesoParams newWalletsOnly() {
        return new SospesoParams(sponsor, program, lamportsTotal, maxPerClaim, maxClaims, expiryTs, true);
    }

    public PublicKey sponsor() {
        return sponsor;
    }

    public PublicKey program() {
        return program;
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
                && sponsor.equals(p.sponsor)
                && program.equals(p.program);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sponsor, program, lamportsTotal, maxPerClaim, maxClaims, expiryTs, newWalletOnly);
    }
}
