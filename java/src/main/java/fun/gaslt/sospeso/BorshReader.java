package fun.gaslt.sospeso;

import java.util.Arrays;

import org.p2p.solanaj.core.PublicKey;

/**
 * A minimal little-endian Borsh decoder, the inverse of {@link BorshWriter}.
 *
 * <p>Used to decode Anchor account data after skipping the leading 8-byte
 * account discriminator. Reads advance an internal cursor and throw if the
 * underlying buffer is too short.
 */
public final class BorshReader {

    private final byte[] data;
    private int pos;

    public BorshReader(byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
    }

    /** Skip {@code n} bytes (e.g. the account discriminator). */
    public BorshReader skip(int n) {
        require(n);
        pos += n;
        return this;
    }

    /** Read a single unsigned byte as an int in [0, 255]. */
    public int u8() {
        require(1);
        return data[pos++] & 0xff;
    }

    /** Read a Borsh boolean (one byte). */
    public boolean bool() {
        return u8() != 0;
    }

    /** Read a little-endian unsigned 32-bit integer into a long. */
    public long u32() {
        require(4);
        long v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (long) (data[pos++] & 0xff) << (8 * i);
        }
        return v;
    }

    /** Read a little-endian 64-bit integer (raw two's-complement long). */
    public long u64() {
        require(8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (data[pos++] & 0xff) << (8 * i);
        }
        return v;
    }

    /** Read a little-endian signed 64-bit integer. */
    public long i64() {
        return u64();
    }

    /** Read a 32-byte public key. */
    public PublicKey pubkey() {
        require(32);
        byte[] key = Arrays.copyOfRange(data, pos, pos + 32);
        pos += 32;
        return new PublicKey(key);
    }

    /** Number of bytes already consumed. */
    public int position() {
        return pos;
    }

    private void require(int n) {
        if (pos + n > data.length) {
            throw new IllegalArgumentException(
                    "borsh: need " + n + " bytes at offset " + pos + " but only "
                            + (data.length - pos) + " remain");
        }
    }
}
