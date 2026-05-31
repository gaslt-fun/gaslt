package fun.gaslt.cli;

import java.util.Optional;
import java.util.concurrent.Callable;

import fun.gaslt.sospeso.BarRegistry;
import fun.gaslt.sospeso.Bridge;
import fun.gaslt.sospeso.ClaimReceipt;
import fun.gaslt.sospeso.Lamports;
import fun.gaslt.sospeso.Pda;
import fun.gaslt.sospeso.Sospeso;
import fun.gaslt.sospeso.SospesoClient;
import fun.gaslt.sospeso.SospesoProgram;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code gaslt} — a command-line tool for the sospeso gas-abstraction program on
 * Solana. It derives the program's PDAs offline and reads live on-chain state
 * (pools, the registry, and bridges) through a public RPC endpoint using the
 * {@code gaslt-java} SDK.
 */
@Command(
        name = "gaslt",
        mixinStandardHelpOptions = true,
        version = "gaslt-cli 0.1.3 (sospeso_verifier 44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW)",
        description = "Read the gaslt sospeso program on Solana.",
        subcommands = {
                GasltCli.PdaCmd.class,
                GasltCli.ProgramCmd.class,
                GasltCli.PoolCmd.class,
                GasltCli.BridgeCmd.class,
                GasltCli.RegistryCmd.class,
                GasltCli.ClaimCmd.class,
        })
public final class GasltCli implements Callable<Integer> {

    /** Default public mainnet RPC (no API key — safe to ship). */
    static final String DEFAULT_RPC = "https://api.mainnet-beta.solana.com";

    @Override
    public Integer call() {
        // With no subcommand, show usage.
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int code = new CommandLine(new GasltCli()).execute(args);
        System.exit(code);
    }

    private static SospesoClient client(String rpc) {
        return new SospesoClient(new RpcClient(rpc));
    }

    private static String sol(long lamports) {
        return lamports + " lamports (" + String.format("%.9f", Lamports.toSol(lamports)) + " SOL)";
    }

    // ----------------------------------------------------------------------

    @Command(name = "pda", description = "Derive a pool PDA from a sponsor and nonce (offline).")
    static final class PdaCmd implements Callable<Integer> {
        @Option(names = "--sponsor", required = true, description = "Sponsor wallet (base58).")
        String sponsor;

        @Option(names = "--nonce", defaultValue = "0", description = "Pool nonce (default 0).")
        long nonce;

        @Override
        public Integer call() {
            PublicKey sp = new PublicKey(sponsor);
            PublicKey.ProgramDerivedAddress pda = Pda.sospeso(sp, nonce);
            System.out.println("sospeso pool PDA");
            System.out.println("  seeds      : [\"sospeso\", " + sponsor + ", nonce_le(" + nonce + ")]");
            System.out.println("  program    : " + SospesoProgram.PROGRAM_ID.toBase58());
            System.out.println("  address    : " + pda.getAddress().toBase58());
            System.out.println("  bump       : " + pda.getNonce());
            return 0;
        }
    }

    @Command(name = "program", description = "Show the on-chain program account (live RPC).")
    static final class ProgramCmd implements Callable<Integer> {
        @Option(names = "--rpc", defaultValue = DEFAULT_RPC, description = "RPC endpoint.")
        String rpc;

        @Override
        public Integer call() throws Exception {
            var info = client(rpc).rpc().getApi().getAccountInfo(SospesoProgram.PROGRAM_ID);
            System.out.println("sospeso_verifier program");
            System.out.println("  program id : " + SospesoProgram.PROGRAM_ID.toBase58());
            System.out.println("  rpc        : " + rpc);
            if (info == null || info.getValue() == null) {
                System.out.println("  status     : NOT FOUND");
                return 0;
            }
            var v = info.getValue();
            System.out.println("  executable : " + v.isExecutable());
            System.out.println("  owner      : " + v.getOwner());
            System.out.println("  lamports   : " + (long) v.getLamports());
            return 0;
        }
    }

    @Command(name = "pool", description = "Fetch and decode a pool account (live RPC).")
    static final class PoolCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Pool PDA address (base58).")
        String address;

        @Option(names = "--rpc", defaultValue = DEFAULT_RPC, description = "RPC endpoint.")
        String rpc;

        @Override
        public Integer call() throws Exception {
            PublicKey addr = new PublicKey(address);
            Optional<Sospeso> pool = client(rpc).fetchSospeso(addr);
            if (pool.isEmpty()) {
                System.out.println("No pool account at " + address);
                System.out.println("  (nothing is stored at this address on " + rpc + ")");
                return 0;
            }
            Sospeso p = pool.get();
            long now = System.currentTimeMillis() / 1000L;
            System.out.println("sospeso pool " + address);
            System.out.println("  sponsor          : " + p.sponsor().toBase58());
            System.out.println("  lamports total   : " + sol(p.lamportsTotal()));
            System.out.println("  lamports remain  : " + sol(p.lamportsRemaining()));
            System.out.println("  max per claim    : " + (p.maxPerClaim() == 0 ? "no cap" : sol(p.maxPerClaim())));
            System.out.println("  claims           : " + p.claimsCount()
                    + (p.maxClaims() == 0 ? " (unbounded)" : " / " + p.maxClaims()));
            System.out.println("  expiry ts        : " + (p.expiryTs() == 0 ? "never" : p.expiryTs()));
            System.out.println("  new wallet only  : " + p.newWalletOnly());
            System.out.println("  utilization      : " + p.utilizationBps() + " bps");
            System.out.println("  expired now      : " + p.isExpired(now));
            return 0;
        }
    }

    @Command(name = "bridge", description = "Derive and read a bridge account by authority (live RPC).")
    static final class BridgeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Bridge authority wallet (base58).")
        String authority;

        @Option(names = "--rpc", defaultValue = DEFAULT_RPC, description = "RPC endpoint.")
        String rpc;

        @Override
        public Integer call() throws Exception {
            PublicKey auth = new PublicKey(authority);
            PublicKey pda = Pda.bridge(auth).getAddress();
            System.out.println("bridge for authority " + authority);
            System.out.println("  bridge PDA : " + pda.toBase58() + "  (seeds [\"bridge\", authority])");
            Optional<Bridge> bridge = client(rpc).fetchBridge(auth);
            if (bridge.isEmpty()) {
                System.out.println("  status     : NOT REGISTERED on " + rpc);
                return 0;
            }
            Bridge b = bridge.get();
            System.out.println("  kind       : " + b.kind());
            System.out.println("  endpoint   : " + b.endpoint());
            System.out.println("  label      : " + b.label());
            System.out.println("  enabled    : " + b.enabled());
            System.out.println("  registered : " + b.registeredAt());
            System.out.println("  updated    : " + b.updatedAt());
            return 0;
        }
    }

    @Command(name = "registry", description = "Read the singleton bar registry (live RPC).")
    static final class RegistryCmd implements Callable<Integer> {
        @Option(names = "--rpc", defaultValue = DEFAULT_RPC, description = "RPC endpoint.")
        String rpc;

        @Override
        public Integer call() throws Exception {
            PublicKey pda = Pda.registry().getAddress();
            System.out.println("bar registry");
            System.out.println("  registry PDA : " + pda.toBase58() + "  (seeds [\"registry\"])");
            Optional<BarRegistry> reg = client(rpc).fetchRegistry();
            if (reg.isEmpty()) {
                System.out.println("  status       : NOT INITIALISED on " + rpc);
                return 0;
            }
            BarRegistry r = reg.get();
            System.out.println("  authority    : " + r.authority().toBase58());
            System.out.println("  total pools  : " + r.totalPools());
            System.out.println("  total claims : " + r.totalClaims());
            System.out.println("  suspended    : " + r.totalSuspendedLamports() + " lamports");
            System.out.println("  last update  : " + r.lastUpdateTs());
            return 0;
        }
    }

    @Command(name = "claim", description = "Check whether a beneficiary has claimed from a pool (live RPC).")
    static final class ClaimCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Pool PDA address (base58).")
        String pool;

        @Parameters(index = "1", description = "Beneficiary wallet (base58).")
        String beneficiary;

        @Option(names = "--rpc", defaultValue = DEFAULT_RPC, description = "RPC endpoint.")
        String rpc;

        @Override
        public Integer call() throws Exception {
            PublicKey poolKey = new PublicKey(pool);
            PublicKey ben = new PublicKey(beneficiary);
            PublicKey receipt = Pda.claimReceipt(poolKey, ben).getAddress();
            System.out.println("claim receipt for " + beneficiary);
            System.out.println("  receipt PDA : " + receipt.toBase58() + "  (seeds [\"claim\", pool, beneficiary])");
            Optional<ClaimReceipt> r = client(rpc).fetchClaimReceipt(poolKey, ben);
            if (r.isEmpty()) {
                System.out.println("  claimed     : no (no receipt on " + rpc + ")");
                return 0;
            }
            System.out.println("  claimed     : yes");
            System.out.println("  amount      : " + sol(r.get().amount()));
            System.out.println("  ts          : " + r.get().ts());
            return 0;
        }
    }
}
