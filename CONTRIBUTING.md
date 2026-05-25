# Contributing to gaslt

Thanks for your interest in the sospeso protocol. This repository is the
reference implementation; contributions that sharpen correctness, documentation,
and test coverage are very welcome.

## Layout

| Path | What it is |
|------|-----------|
| `crates/gaslt-core` | Chain-agnostic protocol logic (registry, escrow, claim, eligibility, rate, matching). |
| `programs/sospeso-verifier` | The on-chain Anchor program. |
| `sdk` | TypeScript client for the relayer service. |
| `docs` | Protocol and architecture notes. |

## Building

```bash
# Rust core (host toolchain)
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --all -- --check

# TypeScript SDK
cd sdk
npm ci
npm run build
npm test
```

The on-chain program builds with the Solana toolchain:

```bash
anchor build
anchor test
```

## Guidelines

- Keep `gaslt-core` free of chain SDK dependencies; pass time and wallet
  metadata in as plain values so rules stay unit-testable.
- Every new rule needs a test. The decision functions are pure by design.
- Run `cargo fmt` and `cargo clippy` before opening a pull request; CI enforces
  both with `-D warnings`.
- Match existing error variants in `ProtocolError` rather than introducing
  string errors, so the relayer can map rejections precisely.

## Reporting issues

Open an issue with a minimal reproduction. For anything touching escrow
accounting or claim eligibility, include the pool parameters and the timestamp
so the path can be reproduced deterministically.
