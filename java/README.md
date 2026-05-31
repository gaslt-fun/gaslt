# gaslt JVM toolkit

A Maven multi-module project for the **sospeso** gas-abstraction program on
Solana (mainnet `44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW`, program v0.1.3).
It mirrors the TypeScript SDK (`../sdk`) and the Rust core (`../crates/gaslt-core`)
on the JVM, and adds a runnable CLI and a deployable HTTP service on top.

| Module | Artifact | What it is |
|--------|----------|------------|
| [`core`](./core) | `fun.gaslt:gaslt-java` | The SDK: PDAs, Borsh codecs, instruction builders, account decoders, an RPC read client. |
| [`cli`](./cli) | `fun.gaslt:gaslt-cli` | A `gaslt` command-line tool that derives PDAs and reads live mainnet state. |
| [`service`](./service) | `fun.gaslt:gaslt-service` | A small HTTP service exposing pools, the registry, and bridges as JSON. |

The CLI and service both depend on `core`, so the on-chain layout is defined once
and reused everywhere.

## Build everything

```bash
# JDK 17+, from this directory
mvn -B verify
```

This compiles all three modules, runs the unit tests (no network — the suite uses
synthetic on-chain buffers and offline routes), and produces two runnable fat
jars:

- `cli/target/gaslt-cli.jar`
- `service/target/gaslt-service.jar`

## Quick taste

```text
$ java -jar cli/target/gaslt-cli.jar program
sospeso_verifier program
  program id : 44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW
  rpc        : https://api.mainnet-beta.solana.com
  executable : true
  owner      : BPFLoaderUpgradeab1e11111111111111111111111
  lamports   : 1141440
```

```text
$ java -jar cli/target/gaslt-cli.jar bridge 9prpwEhLBsN2V9JmGbryyxmsT87d2Hqc4UhqQG8zWtum
bridge for authority 9prpwEhLBsN2V9JmGbryyxmsT87d2Hqc4UhqQG8zWtum
  bridge PDA : B4smUxyDeW6ptTS6Sd5GsnRD2Pxm2acps5Fqo6HTmEsj  (seeds ["bridge", authority])
  kind       : jvm
  endpoint   : https://gaslt-jvm.up.railway.app
  label      : gaslt-java
  enabled    : true
```

That `jvm` bridge is registered on mainnet by the program and points back at this
toolkit's own service — the SDK decodes it straight from the chain. The service
exposes the same data as JSON:

```text
$ curl -s localhost:8080/health
{"status":"ok","service":"gaslt-service","version":"0.1.3","program":"44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW","rpc":"https://api.mainnet-beta.solana.com"}
```

See each module's README for full usage and more captured output. RPC reads use
the public mainnet endpoint and need no API key.

## License

MIT — see [`../LICENSE`](../LICENSE).
