//! Anchor events emitted by the v0.1.2 instructions.
//!
//! The four original events (`SospesoCreated`, `SospesoClaimed`,
//! `SospesoToppedUp`, `SospesoReclaimed`) still live in `lib.rs` unchanged.
//! These cover the metadata, expiry-extension, registry, and version surfaces
//! added in 0.1.2.

use anchor_lang::prelude::*;

/// Emitted when a pool's metadata account is first created via `set_meta`.
#[event]
pub struct MetaSet {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub category: u8,
    pub ts: i64,
}

/// Emitted on every successful `update_meta`.
#[event]
pub struct MetaUpdated {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub category: u8,
    pub ts: i64,
}

/// Emitted when a metadata account is closed via `clear_meta`.
#[event]
pub struct MetaCleared {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub ts: i64,
}

/// Emitted when a sponsor pushes a pool's expiry further out.
#[event]
pub struct ExpiryExtended {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub old_expiry_ts: i64,
    pub new_expiry_ts: i64,
}

/// Emitted once, when the singleton registry is initialized.
#[event]
pub struct RegistryInitialized {
    pub registry: Pubkey,
    pub authority: Pubkey,
    pub ts: i64,
}

/// Emitted when a pool is folded into the registry aggregate for the first time.
#[event]
pub struct PoolRegistered {
    pub registry: Pubkey,
    pub sospeso: Pubkey,
    pub recorded_lamports: u64,
    pub recorded_claims: u32,
    pub total_pools: u64,
}

/// Emitted when a registered pool's deltas (top-ups, new claims) are re-synced
/// into the registry aggregate.
#[event]
pub struct PoolStatsSynced {
    pub registry: Pubkey,
    pub sospeso: Pubkey,
    pub lamports_delta: u64,
    pub claims_delta: u32,
    pub total_suspended_lamports: u128,
    pub total_claims: u64,
}

/// Emitted by `emit_version`; lets indexers pin the live on-chain version.
#[event]
pub struct VersionEmitted {
    pub version: String,
    pub caller: Pubkey,
    pub ts: i64,
}

// --- v0.1.3: bridge registry events ---

/// Emitted when an off-chain-service bridge is first registered.
#[event]
pub struct BridgeRegistered {
    pub bridge: Pubkey,
    pub authority: Pubkey,
    pub kind: [u8; 16],
    pub enabled: bool,
    pub ts: i64,
}

/// Emitted on every successful `update_bridge`.
#[event]
pub struct BridgeUpdated {
    pub bridge: Pubkey,
    pub authority: Pubkey,
    pub enabled: bool,
    pub ts: i64,
}

/// Emitted when a bridge is revoked (its account closed, rent refunded).
#[event]
pub struct BridgeRevoked {
    pub bridge: Pubkey,
    pub authority: Pubkey,
    pub ts: i64,
}

// --- v0.1.4: bridge pulse event ---

/// Emitted on every `bridge_pulse`; lets indexers track bridge liveness and
/// cumulative relayed work without an account read.
#[event]
pub struct BridgePulsed {
    pub bridge: Pubkey,
    pub authority: Pubkey,
    pub pulse_count: u64,
    pub relayed_claims: u64,
    pub version: u32,
    pub ts: i64,
}
