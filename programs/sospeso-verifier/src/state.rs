//! Account state for the sospeso verifier program.

use anchor_lang::prelude::*;

/// A sponsored gas pool. Funded by a sponsor, drawn down by claims.
///
/// Lamports live directly on this account (it is the escrow). Because the
/// account carries data it must remain rent-exempt, so only `lamports_remaining`
/// is ever debited and the rent floor stays untouched.
#[account]
#[derive(InitSpace)]
pub struct Sospeso {
    /// Wallet that funded the pool and may top up / reclaim.
    pub sponsor: Pubkey,
    /// Program these sponsored transactions are expected to target.
    pub program: Pubkey,
    /// Total lamports committed across the pool's lifetime.
    pub lamports_total: u64,
    /// Lamports still available to claim.
    pub lamports_remaining: u64,
    /// Maximum lamports any single claim may draw (0 = no per-claim cap).
    pub max_per_claim: u64,
    /// Claims served so far.
    pub claims_count: u32,
    /// Maximum number of claims (0 = unbounded by count).
    pub max_claims: u32,
    /// Unix timestamp after which the pool expires (0 = never).
    pub expiry_ts: i64,
    /// Nonce used to derive this PDA from the sponsor.
    pub nonce: u64,
    /// When true, only new wallets may claim.
    pub new_wallet_only: bool,
    /// PDA bump.
    pub bump: u8,
    /// Reserved padding for forward-compatible upgrades.
    pub _padding: [u8; 6],
}
