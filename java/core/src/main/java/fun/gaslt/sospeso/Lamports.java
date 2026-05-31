package fun.gaslt.sospeso;

/**
 * Lamport helpers mirroring the TypeScript SDK's {@code types.ts}.
 *
 * <p>Lamports are the indivisible unit of SOL; there are {@value #LAMPORTS_PER_SOL}
 * lamports in one SOL. On-chain amounts are unsigned 64-bit, so callers that may
 * exceed {@code 2^53} should keep values in {@code long} rather than {@code double}.
 */
public final class Lamports {

    /** Number of lamports in one SOL. */
    public static final long LAMPORTS_PER_SOL = 1_000_000_000L;

    private Lamports() {
    }

    /** Convert a lamport amount to a fractional SOL value. */
    public static double toSol(long lamports) {
        return (double) lamports / (double) LAMPORTS_PER_SOL;
    }

    /** Convert a SOL amount to integer lamports, rounding to the nearest lamport. */
    public static long fromSol(double sol) {
        return Math.round(sol * (double) LAMPORTS_PER_SOL);
    }
}
