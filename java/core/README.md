# gaslt-java

The SDK module of the [gaslt JVM toolkit](../README.md). A Java client for the
**sospeso** gas-abstraction program on Solana. It mirrors the TypeScript SDK
(`../../sdk`) and the chain-agnostic Rust core (`../../crates/gaslt-core`), so a
JVM application can derive the program's PDAs, build its instructions, and decode
its accounts without re-deriving any of the on-chain layout by hand.

It targets the deployed `sospeso_verifier` program (mainnet, v0.1.3):

```
44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW
```

## Requirements

- JDK 17+
- Maven 3.9+

The only runtime dependency is [solanaj](https://github.com/skynetcap/solanaj)
(`com.mmorrell:solanaj`), which supplies `PublicKey`, `AccountMeta`,
`TransactionInstruction`, and the JSON-RPC client.

## Install

```xml
<dependency>
  <groupId>fun.gaslt</groupId>
  <artifactId>gaslt-java</artifactId>
  <version>0.1.3</version>
</dependency>
```

## Program-derived addresses

The pool and receipt PDAs use the same seeds as the program. A pool is keyed on
`(sponsor, nonce)`; a receipt on `(pool, beneficiary)`.

```java
import fun.gaslt.sospeso.Pda;
import org.p2p.solanaj.core.PublicKey;

PublicKey sponsor = new PublicKey("So11111111111111111111111111111111111111112");

// ["sospeso", sponsor, nonce_le(8)]
PublicKey pool = Pda.sospeso(sponsor, 7L).getAddress();

// ["claim", sospeso, beneficiary]
PublicKey beneficiary = new PublicKey("ComputeBudget111111111111111111111111111111");
PublicKey receipt = Pda.claimReceipt(pool, beneficiary).getAddress();
```

## Instructions

`SospesoInstructions` builds every program instruction. Each returns a solanaj
`TransactionInstruction` (8-byte Anchor discriminator + Borsh-encoded arguments,
with accounts in the program's declared order) that you add to a `Transaction`
and sign with your own keypair handling.

```java
import fun.gaslt.sospeso.SospesoInstructions;
import fun.gaslt.sospeso.SospesoParams;
import fun.gaslt.sospeso.Lamports;
import org.p2p.solanaj.core.TransactionInstruction;

// Open a new-wallet-only onboarding pool funded with 2 SOL.
SospesoParams params = SospesoParams.of(sponsor, Lamports.fromSol(2.0))
        .withMaxPerClaim(Lamports.fromSol(0.005))
        .withMaxClaims(100)
        .newWalletsOnly();

TransactionInstruction create = SospesoInstructions.createSospeso(params, 7L);

// A relayer claims gas on a beneficiary's behalf (payer != beneficiary).
TransactionInstruction claim =
        SospesoInstructions.claimSospeso(relayer, beneficiary, pool, Lamports.fromSol(0.002));

// Sponsor-only operations.
TransactionInstruction topUp   = SospesoInstructions.topUp(sponsor, pool, Lamports.fromSol(1.0));
TransactionInstruction reclaim = SospesoInstructions.reclaim(sponsor, pool); // after expiry
```

| Instruction | Accounts (in order) | Args |
|-------------|---------------------|------|
| `createSospeso` | sponsor (s, w), pool PDA (w), system program | nonce, amount, maxPerClaim, maxClaims, expiryTs, newWalletOnly |
| `claimSospeso` | payer (s, w), beneficiary (w), pool (w), receipt PDA (w), system program | amount |
| `topUp` | sponsor (s, w), pool (w), system program | amount |
| `reclaim` | sponsor (s, w), pool (w) | — |

`s` = signer, `w` = writable. The beneficiary does **not** sign, so a relayer can
pay the receipt rent and claim on its behalf (the Octane fee-payer pattern).

Program v0.1.3 adds metadata, expiry, registry, and bridge instructions — also
built here: `setMeta` / `updateMeta` / `clearMeta`, `extendExpiry`,
`initRegistry` / `registerPool` / `syncPoolStats` / `emitVersion`, and
`registerBridge` / `updateBridge` / `revokeBridge`.

## Reading accounts

`SospesoClient` wraps a solanaj `RpcClient` and decodes account data into the
`Sospeso` and `ClaimReceipt` models. The 8-byte account discriminator is
validated on decode.

```java
import fun.gaslt.sospeso.SospesoClient;
import fun.gaslt.sospeso.Sospeso;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;

SospesoClient client = new SospesoClient(new RpcClient(Cluster.MAINNET));

Sospeso pool = client.fetchSospeso(sponsor, 7L).orElseThrow();
long now = System.currentTimeMillis() / 1000;
if (pool.canCover(Lamports.fromSol(0.002), now)) {
    // safe to claim from this pool
}
System.out.println("utilisation: " + pool.utilizationBps() + " bps");

// The receipt's existence is the double-claim guard.
boolean already = client.hasClaimed(pool, beneficiary);
```

`Sospeso` exposes the same predicates as the Rust core — `isExpired(now)`,
`claimsExhausted()`, `canCover(amount, now)`, and `utilizationBps()` — so
off-chain code reasons about a pool exactly as the program does.

### v0.1.3 registry and bridge accounts

Program v0.1.3 adds a bar registry and off-chain bridge directory. The client
reads them too, with the matching PDA helpers on `Pda`:

```java
// Singleton registry at ["registry"].
client.fetchRegistry().ifPresent(r ->
    System.out.println(r.totalPools() + " pools, " + r.totalClaims() + " claims"));

// A bridge advertises an off-chain service, keyed at ["bridge", authority].
client.fetchBridge(authority).ifPresent(b ->
    System.out.println(b.kind() + " -> " + b.endpoint() + " (enabled=" + b.enabled() + ")"));

// Per-pool registry entry ["regentry", pool] and metadata ["meta", pool].
client.fetchRegistryEntry(pool);
client.fetchMeta(pool);
```

`BarRegistry`, `RegistryEntry`, `Bridge`, and `SospesoMeta` each have a
`from(byte[])` decoder validated against their IDL discriminator.

## Serialization helpers

`BorshWriter` / `BorshReader` provide the little-endian Borsh primitives
(`u8`, `bool`, `u32`, `u64`, `i64`, `u128`, length-prefixed strings, fixed-size
null-padded ascii, 32-byte pubkey) the layout uses, and `SospesoProgram` computes
the Anchor discriminators (`sha256("global:<ix>")[..8]`,
`sha256("account:<Acct>")[..8]`). These are the building blocks the higher-level
classes are built from; you can use them directly for custom encoding.

## Build and test

```bash
# from the java/ parent (builds the SDK and its dependents)
mvn -B verify

# or just this module
mvn -B -pl core -am verify
```

The test suite covers PDA determinism and seed layout, the exact discriminator
values (pinned to the program IDL), Borsh round-trips, instruction encoding
(args + account metas), and account decoding against synthetic on-chain buffers.

## License

MIT — see [`../../LICENSE`](../../LICENSE).
