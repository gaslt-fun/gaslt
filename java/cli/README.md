# gaslt-cli

A command-line tool for the **sospeso** gas-abstraction program on Solana. It is
the [gaslt JVM toolkit](../README.md)'s CLI module: it derives the program's PDAs
offline and reads live mainnet state through the `gaslt-java` SDK over a public
RPC endpoint (no API key required).

## Build and run

```bash
# from the java/ parent
mvn -B -pl cli -am package
java -jar cli/target/gaslt-cli.jar --help
```

```text
Usage: gaslt [-hV] [COMMAND]
Read the gaslt sospeso program on Solana.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  pda       Derive a pool PDA from a sponsor and nonce (offline).
  program   Show the on-chain program account (live RPC).
  pool      Fetch and decode a pool account (live RPC).
  bridge    Derive and read a bridge account by authority (live RPC).
  registry  Read the singleton bar registry (live RPC).
  claim     Check whether a beneficiary has claimed from a pool (live RPC).
```

Network commands accept `--rpc <url>` (default `https://api.mainnet-beta.solana.com`).

## Examples

All output below is captured from real runs against Solana mainnet.

### Derive a pool PDA (offline)

```text
$ java -jar cli/target/gaslt-cli.jar pda \
    --sponsor So11111111111111111111111111111111111111112 --nonce 7
sospeso pool PDA
  seeds      : ["sospeso", So11111111111111111111111111111111111111112, nonce_le(7)]
  program    : 44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW
  address    : DmQXnkV9VeVHb2YCq2ZpvZUkSsqz4p2MDq2Heko8qvmg
  bump       : 255
```

### Read the on-chain program (live)

```text
$ java -jar cli/target/gaslt-cli.jar program
sospeso_verifier program
  program id : 44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW
  rpc        : https://api.mainnet-beta.solana.com
  executable : true
  owner      : BPFLoaderUpgradeab1e11111111111111111111111
  lamports   : 1141440
```

### Read the registry and a bridge (live)

The registry and bridges are program v0.1.3 features. When an account has not been
created on chain yet, the tool says so plainly and still prints the derived PDA:

```text
$ java -jar cli/target/gaslt-cli.jar registry
bar registry
  registry PDA : 6s2rvrg8ytn3mRJJGX6cgZfgDGMMeokC9cD7pJ4v82bm  (seeds ["registry"])
  status       : NOT INITIALISED on https://api.mainnet-beta.solana.com

$ java -jar cli/target/gaslt-cli.jar bridge So11111111111111111111111111111111111111112
bridge for authority So11111111111111111111111111111111111111112
  bridge PDA : FcE9VbfBV1E91D9iyxtuEZbVrtu2KbJAkquJByqheypH  (seeds ["bridge", authority])
  status     : NOT REGISTERED on https://api.mainnet-beta.solana.com
```

Once a pool, registry, or bridge is created, the same commands print the decoded
fields (`pool` shows sponsor, lamports total/remaining, claim counts, expiry, and
utilisation; `bridge` shows kind/endpoint/label/enabled).

## License

MIT — see [`../../LICENSE`](../../LICENSE).
