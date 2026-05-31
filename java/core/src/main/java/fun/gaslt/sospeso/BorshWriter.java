package fun.gaslt.sospeso;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.p2p.solanaj.core.PublicKey;

/**
 * A minimal little-endian Borsh encoder covering the types used by the sospeso
 * program: {@code u8}, {@code bool}, {@code u32}, {@code u64}, {@code i64},
 * {@code u128}, length-prefixed strings, and 32-byte public keys.
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

    /** Append a little-endian unsigned 128-bit integer. */
    public BorshWriter u128(BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 128) {
            throw new IllegalArgumentException("u128 out of range: " + value);
        }
        for (int i = 0; i < 16; i++) {
            out.write(value.shiftRight(8 * i).and(BigInteger.valueOf(0xff)).intValue());
        }
        return this;
    }

    /** Append a Borsh string: a little-endian {@code u32} byte length then UTF-8 bytes. */
    public BorshWriter string(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        u32(utf8.length);
        out.writeBytes(utf8);
        return this;
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

    /**
     * Append a fixed-size {@code [u8; length]} field, NUL-padding (or truncating)
     * {@code value} to exactly {@code length} bytes. Borsh encodes a fixed-size
     * byte array as the raw bytes with no length prefix, matching the program's
     * ascii null-padded fields ({@code kind}, {@code endpoint}, {@code label}).
     */
    public BorshWriter fixedBytes(byte[] value, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative: " + length);
        }
        byte[] fixed = new byte[length];
        System.arraycopy(value, 0, fixed, 0, Math.min(value.length, length));
        out.writeBytes(fixed);
        return this;
    }

    /** Snapshot the accumulated bytes. */
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
