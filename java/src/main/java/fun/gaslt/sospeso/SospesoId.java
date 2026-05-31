package fun.gaslt.sospeso;

import java.util.Objects;

import org.p2p.solanaj.core.PublicKey;

/**
 * A registry-level identifier for a sospeso, distinct from its on-chain PDA.
 *
 * <p>Mirrors the {@code SospesoId} type in the Rust {@code gaslt-core} crate,
 * including its deterministic {@link #derive(PublicKey, long)} form so the same
 * (sponsor, nonce) pair yields the same id across the Rust, TypeScript, and Java
 * clients.
 */
public final class SospesoId {

    private final String value;

    private SospesoId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Wrap an arbitrary string as an id. */
    public static SospesoId of(String value) {
        return new SospesoId(value);
    }

    /**
     * Derive a deterministic id from a sponsor and nonce, matching the Rust
     * {@code SospesoId::derive}: {@code spo_<short>_<nonce>} where {@code short}
     * is the {@code aabb..yyzz} hex fragment of the sponsor key.
     */
    public static SospesoId derive(PublicKey sponsor, long nonce) {
        return new SospesoId("spo_" + shortHex(sponsor) + "_" + Long.toUnsignedString(nonce));
    }

    /** The {@code aabb..yyzz} hex fragment used in {@link #derive}. */
    static String shortHex(PublicKey key) {
        byte[] b = key.toByteArray();
        return String.format(
                "%02x%02x..%02x%02x",
                b[0] & 0xff, b[1] & 0xff, b[30] & 0xff, b[31] & 0xff);
    }

    /** The underlying string value. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SospesoId other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
