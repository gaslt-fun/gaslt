package fun.gaslt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import fun.gaslt.sospeso.Pda;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import picocli.CommandLine;

/** Offline CLI tests: argument parsing and the network-free {@code pda} command. */
class GasltCliTest {

    private record Run(int code, String out) {
    }

    private static Run run(String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new GasltCli()).execute(args);
            return new Run(code, buf.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void helpExitsZeroAndListsSubcommands() {
        Run r = run("--help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("pda"));
        assertTrue(r.out().contains("pool"));
        assertTrue(r.out().contains("bridge"));
        assertTrue(r.out().contains("registry"));
    }

    @Test
    void pdaCommandDerivesTheSameAddressAsTheSdk() {
        String sponsor = "So11111111111111111111111111111111111111112";
        long nonce = 7;
        String expected = Pda.sospeso(new PublicKey(sponsor), nonce).getAddress().toBase58();

        Run r = run("pda", "--sponsor", sponsor, "--nonce", "7");
        assertEquals(0, r.code());
        assertTrue(r.out().contains(expected), "output should contain derived PDA " + expected);
    }

    @Test
    void missingRequiredOptionFails() {
        Run r = run("pda");
        assertEquals(2, r.code()); // picocli usage error exit code
    }
}
