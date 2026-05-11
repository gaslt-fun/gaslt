# The sospeso protocol

A *caffè sospeso* is a coffee paid in advance for a stranger who cannot afford
one. The sospeso protocol applies the same idea to transaction fees: a sponsor
pre-pays gas into a pool, and wallets that hold no SOL draw on it to transact.

## Roles

| Role | Responsibility |
|------|----------------|
| Sponsor | Funds a pool (`create_sospeso`), may top it up or reclaim after expiry. |
| Beneficiary | A wallet that needs gas; signs the transaction it wants executed. |
| Relayer | Pays the network fee (the fee payer) and reimburses itself from a matched pool. |
| Verifier | The on-chain program that holds escrow and enforces the claim rules. |

## Lifecycle

1. **Create.** A sponsor opens a pool with a total budget, an optional per-claim
   cap, an optional total claim count, an optional expiry, and an optional
   new-wallet-only flag.
2. **Match.** When a beneficiary needs gas, the matching engine selects the best
   eligible pool: the smallest remaining budget that still covers the request,
   breaking ties toward the nearest expiry.
3. **Sponsor.** The relayer validates the beneficiary's partially-signed
   transaction, becomes the fee payer, submits it, and records the fee it
   fronted.
4. **Claim.** The fronted lamports are reimbursed from the pool. A claim receipt
