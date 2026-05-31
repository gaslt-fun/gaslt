package fun.gaslt.sospeso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

class BorshRoundTripTest {

    @Test
    void scalarsRoundTrip() {
        PublicKey key = new PublicKey("So11111111111111111111111111111111111111112");
        byte[] bytes = new BorshWriter()
                .u8(0xAB)
                .bool(true)
                .u32(4_000_000_000L)   // > Integer.MAX_VALUE, exercises unsigned handling
                .u64(9_123_456_789L)
                .i64(-42L)
                .pubkey(key)
                .toByteArray();

        BorshReader r = new BorshReader(bytes);
        assertEquals(0xAB, r.u8());
        assertTrue(r.bool());
        assertEquals(4_000_000_000L, r.u32());
        assertEquals(9_123_456_789L, r.u64());
        assertEquals(-42L, r.i64());
        assertEquals(key, r.pubkey());
        assertEquals(bytes.length, r.position());
    }

    @Test
    void u64IsLittleEndian() {
        byte[] bytes = new BorshWriter().u64(1).toByteArray();
        assertEquals("0100000000000000", HexFormat.of().formatHex(bytes));
    }

    @Test
    void boolEncodesSingleByte() {
        assertEquals("01", HexFormat.of().formatHex(new BorshWriter().bool(true).toByteArray()));
        assertEquals("00", HexFormat.of().formatHex(new BorshWriter().bool(false).toByteArray()));
        assertFalse(new BorshReader(new byte[] {0}).bool());
    }

    @Test
    void readingPastEndThrows() {
        BorshReader r = new BorshReader(new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, r::u64);
    }
}
