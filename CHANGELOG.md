# Changelog

All notable changes to this project are documented here. The format follows
Keep a Changelog, and the project adheres to semantic versioning.

## 0.4.2

- Tighten the escrow rent-floor guard so a debit can never leave a pool below
  the rent-exempt threshold.
- Add `RateLimiter::sweep` to drop stale fixed windows and bound memory.
- SDK: expose `selectBestPool` and the static `canCover` / `isEligible` helpers
  so clients can pre-filter pools before calling the relayer.

## 0.4.0

- Introduce the matching engine: smallest-covering-budget selection with a
  nearest-expiry tie-break.
- Add per-(wallet, sospeso) double-claim tracking to the registry.
- On-chain: emit `SospesoCreated`, `SospesoClaimed`, and `SospesoReclaimed`
  events for indexers.

## 0.3.0

- Split eligibility out of matching so new-wallet gating and program matching
  can be reused independently.
- Add `top_up` and `reclaim` instructions to the verifier program.

## 0.2.0

- First end-to-end registry flow: open a pool, claim against it, read stats.
- Borsh serialization for the core account types.

## 0.1.0

- Initial protocol types and escrow accounting.
