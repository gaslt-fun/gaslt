package fun.gaslt.sospeso;

import java.io.ByteArrayOutputStream;

import org.p2p.solanaj.core.PublicKey;

/**
 * A minimal little-endian Borsh encoder covering the scalar types used by the
 * sospeso program's instruction arguments: {@code u8}, {@code bool}, {@code u32},
 * {@code u64}, {@code i64}, and 32-byte public keys.
 *
 * <p>Borsh encodes integers in little-endian with no length prefix, which is what
 * Anchor expects after the 8-byte instruction discriminator.
 */
public final class BorshWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** Append a single unsigned byte. */
    public BorshWriter u8(int value) {
        out.write(value & 0xff);
        return this;
    }

    /** Append a Borsh boolean (one byte, 0 or 1). */
    public BorshWriter bool(boolean value) {
        out.write(value ? 1 : 0);
        return this;
    }

    /** Append a little-endian unsigned 32-bit integer. */
    public BorshWriter u32(long value) {
        for (int i = 0; i < 4; i++) {
            out.write((int) ((value >>> (8 * i)) & 0xff));
        }
        return this;
    }

    /** Append a little-endian unsigned 64-bit integer. */
    public BorshWriter u64(long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >>> (8 * i)) & 0xff));
        }
        return this;
    }

    /** Append a little-endian signed 64-bit integer (two's complement). */
    public BorshWriter i64(long value) {
        return u64(value);
    }

    /** Append a 32-byte public key in its raw on-chain byte order. */
    public BorshWriter pubkey(PublicKey key) {
        byte[] bytes = key.toByteArray();
        if (bytes.length != 32) {
            throw new IllegalArgumentException("public key must be 32 bytes, was " + bytes.length);
        }
        out.writeBytes(bytes);
        return this;
    }

    /** Append raw bytes verbatim (used for the instruction discriminator). */
    public BorshWriter bytes(byte[] raw) {
        out.writeBytes(raw);
        return this;
    }

    /** Snapshot the accumulated bytes. */
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
