//! On-chain account state for sospeso_verifier.

use anchor_lang::prelude::*;

/// A suspended-gas escrow pool opened by a sponsor.
///
/// PDA: seeds `[b"sospeso", sponsor, nonce.to_le_bytes()]`.
///
/// Actual lamports held by the account = rent-exempt minimum + `lamports_remaining`.
/// `lamports_total` is the cumulative amount ever funded (create + top_ups); it is
/// informational and never decreases on claim.
#[account]
pub struct Sospeso {
    /// Wallet that opened and funds the pool; the only party allowed to top up or reclaim.
    pub sponsor: Pubkey,
    /// Cumulative lamports funded into the pool (create + all top_ups). Informational.
    pub lamports_total: u64,
    /// Lamports still claimable. Debited on each claim, zeroed on reclaim.
    pub lamports_remaining: u64,
    /// Hard cap on a single claim.
    pub max_per_claim: u64,
    /// Number of successful claims so far.
    pub claims_count: u32,
    /// Maximum number of claims the pool will ever honour.
    pub max_claims: u32,
    /// Unix timestamp after which claims are rejected and reclaim becomes possible.
    pub expiry_ts: i64,
    /// If true, off-chain eligibility (relayer) should restrict claims to new wallets.
    /// Enforced off-chain by the relayer; stored on-chain so the policy is auditable.
    pub new_wallet_only: bool,
    /// Sponsor-chosen nonce allowing many concurrent pools per sponsor.
    pub nonce: u64,
    /// PDA bump.
    pub bump: u8,
}

impl Sospeso {
    /// Byte length of the serialized fields (excludes the 8-byte Anchor discriminator).
    /// 32 + 8 + 8 + 8 + 4 + 4 + 8 + 1 + 8 + 1 = 82
    pub const LEN: usize = 32 + 8 + 8 + 8 + 4 + 4 + 8 + 1 + 8 + 1;
}

/// Proof that a given beneficiary has already claimed from a given sospeso.
///
/// PDA: seeds `[b"claim", sospeso, beneficiary]`. Created (`init`) inside
/// `claim_sospeso`; its existence alone makes a second claim by the same
/// beneficiary fail, so it is the on-chain double-claim guard.
#[account]
pub struct ClaimReceipt {
    /// The wallet that claimed.
    pub beneficiary: Pubkey,
    /// Lamports granted in the (single) claim.
    pub amount: u64,
    /// Unix timestamp of the claim.
    pub ts: i64,
    /// PDA bump.
    pub bump: u8,
}

impl ClaimReceipt {
    /// 32 + 8 + 8 + 1 = 49 (excludes the 8-byte discriminator).
    pub const LEN: usize = 32 + 8 + 8 + 1;
}

// ---------------------------------------------------------------------------
// v0.1.2 additions -- all new account types. None of these change the byte
// layout of `Sospeso` or `ClaimReceipt` above, so every previously created
// pool and receipt keeps deserializing unchanged (full backward compatibility).
// ---------------------------------------------------------------------------

/// Maximum byte length of a [`SospesoMeta::label`].
pub const MAX_LABEL_LEN: usize = 32;
/// Maximum byte length of a [`SospesoMeta::description`].
pub const MAX_DESCRIPTION_LEN: usize = 200;
/// Maximum byte length of a [`SospesoMeta::uri`].
pub const MAX_URI_LEN: usize = 200;
/// Highest accepted [`SospesoMeta::category`] value (inclusive).
pub const MAX_CATEGORY: u8 = 8;

/// Off-chain-facing descriptive metadata for a pool, stored in its own PDA so
/// that the hot-path `Sospeso` account stays small and its layout frozen.
///
/// PDA: seeds `[b"meta", sospeso]`. One meta account per pool. Created with
/// `set_meta`, mutated with `update_meta`, and closed (rent refunded to the
/// sponsor) with `clear_meta`. Only the pool's sponsor may write to it.
#[account]
pub struct SospesoMeta {
    /// The pool this metadata describes.
    pub sospeso: Pubkey,
    /// Sponsor of the pool; the only writer.
    pub sponsor: Pubkey,
    /// Short human-readable name (<= [`MAX_LABEL_LEN`] bytes).
    pub label: String,
    /// Longer description (<= [`MAX_DESCRIPTION_LEN`] bytes).
    pub description: String,
    /// Off-chain resource URI, e.g. an icon or campaign page (<= [`MAX_URI_LEN`] bytes).
    pub uri: String,
    /// Sponsor-defined category tag in `0..=MAX_CATEGORY`.
    pub category: u8,
    /// Unix timestamp the meta was first set.
    pub created_ts: i64,
    /// Unix timestamp of the last `update_meta`.
    pub updated_ts: i64,
    /// PDA bump.
    pub bump: u8,
}

impl SospesoMeta {
    /// Serialized length excluding the 8-byte discriminator. Strings are sized
    /// at their maximum (4-byte length prefix + max bytes) so the account never
    /// needs reallocation across updates.
    /// 32 + 32 + (4+32) + (4+200) + (4+200) + 1 + 8 + 8 + 1 = 526
    pub const LEN: usize = 32
        + 32
        + (4 + MAX_LABEL_LEN)
        + (4 + MAX_DESCRIPTION_LEN)
        + (4 + MAX_URI_LEN)
        + 1
        + 8
        + 8
        + 1;
}

/// Singleton global statistics account ("the bar's ledger"): an aggregate roll-up
/// across registered pools. PDA: seeds `[b"registry"]`.
///
/// Updating it is opt-in via `register_pool` / `sync_pool_stats`; the four core
/// instructions never touch it, preserving their behaviour and compute cost.
#[account]
pub struct BarRegistry {
    /// Wallet that initialized the registry. Informational; the registry is a
    /// permissionless aggregate and anyone may register their own pools into it.
    pub authority: Pubkey,
    /// Count of distinct pools registered (one per [`RegistryEntry`]).
    pub total_pools: u64,
    /// Sum of every registered pool's `lamports_total`, kept in sync via
    /// `sync_pool_stats`. `u128` so it cannot overflow across many large pools.
    pub total_suspended_lamports: u128,
    /// Sum of every registered pool's `claims_count`.
    pub total_claims: u64,
    /// Unix timestamp the registry was created.
    pub created_ts: i64,
    /// Unix timestamp of the most recent registry mutation.
    pub last_update_ts: i64,
    /// PDA bump.
    pub bump: u8,
}

impl BarRegistry {
    /// 32 + 8 + 16 + 8 + 8 + 8 + 1 = 81 (excludes the 8-byte discriminator).
    pub const LEN: usize = 32 + 8 + 16 + 8 + 8 + 8 + 1;
}

/// Per-pool membership record inside the registry. Its `init` makes a second
/// `register_pool` for the same pool fail (the existence-is-the-guard pattern,
/// same idea as [`ClaimReceipt`]), preventing a pool from being double-counted.
///
/// PDA: seeds `[b"regentry", sospeso]`.
#[account]
pub struct RegistryEntry {
    /// The registered pool.
    pub sospeso: Pubkey,
    /// The registry this entry belongs to.
    pub registry: Pubkey,
    /// `sospeso.lamports_total` last folded into the registry aggregate.
    pub recorded_lamports: u64,
    /// `sospeso.claims_count` last folded into the registry aggregate.
    pub recorded_claims: u32,
    /// Unix timestamp the pool was first registered.
    pub registered_ts: i64,
    /// Unix timestamp of the last `sync_pool_stats`.
    pub synced_ts: i64,
    /// PDA bump.
    pub bump: u8,
}

impl RegistryEntry {
    /// 32 + 32 + 8 + 4 + 8 + 8 + 1 = 93 (excludes the 8-byte discriminator).
    pub const LEN: usize = 32 + 32 + 8 + 4 + 8 + 8 + 1;
}

// ---------------------------------------------------------------------------
// v0.1.3 additions -- the off-chain "bridge" registry. A `Bridge` account links
// the on-chain program to an off-chain service (e.g. a JVM/Java backend) so that
// indexers, clients, and the service itself can discover the canonical endpoint
// on-chain. All fields are FIXED-SIZE byte arrays (ascii, null-padded) so any
// language -- notably the Java service that consumes this -- can decode the
// account at constant byte offsets without a Borsh/string-length parser.
//
// None of this changes the byte layout of any account above, so every existing
// Sospeso / ClaimReceipt / SospesoMeta / BarRegistry / RegistryEntry keeps
// deserializing unchanged (full backward compatibility).
// ---------------------------------------------------------------------------

/// Fixed length of [`Bridge::kind`] (ascii, null-padded).
pub const BRIDGE_KIND_LEN: usize = 16;
/// Fixed length of [`Bridge::endpoint`] (ascii URI, null-padded).
pub const BRIDGE_ENDPOINT_LEN: usize = 160;
/// Fixed length of [`Bridge::label`] (ascii, null-padded).
pub const BRIDGE_LABEL_LEN: usize = 48;

/// On-chain registry record connecting the program to an off-chain service.
///
/// PDA: seeds `[b"bridge", authority]` -- one bridge per authority. Created by
/// `register_bridge`, mutated by `update_bridge`, and closed (rent refunded to
/// the authority) by `revoke_bridge`. Only the recorded `authority` may write.
///
/// Every field is a fixed-width primitive or byte array, so the serialized
/// account has a constant layout the consuming Java service can read by offset:
///
/// ```text
///   offset  size  field
///   0       8     anchor discriminator
///   8       32    authority      (Pubkey)
///   40      16    kind           ([u8; 16],  ascii null-padded)
///   56      160   endpoint       ([u8; 160], ascii null-padded)
///   216     48    label          ([u8; 48],  ascii null-padded)
///   264     1     enabled        (bool, 0/1)
///   265     8     registered_at  (i64, little-endian)
///   273     8     updated_at     (i64, little-endian)
///   281     1     bump           (u8)
///   ---     282   total account size
/// ```
#[account]
pub struct Bridge {
    /// Wallet that registered and controls this bridge; the only writer.
    pub authority: Pubkey,
    /// Short ascii tag for the bridge kind, null-padded (e.g. `"jvm"`).
    pub kind: [u8; BRIDGE_KIND_LEN],
    /// Off-chain service endpoint URI, ascii, null-padded.
    pub endpoint: [u8; BRIDGE_ENDPOINT_LEN],
    /// Human-readable label, ascii, null-padded.
    pub label: [u8; BRIDGE_LABEL_LEN],
    /// Whether the bridge is currently advertised as live.
    pub enabled: bool,
    /// Unix timestamp the bridge was first registered.
    pub registered_at: i64,
    /// Unix timestamp of the last `update_bridge`.
    pub updated_at: i64,
    /// PDA bump.
    pub bump: u8,
}

impl Bridge {
    /// Serialized length excluding the 8-byte discriminator.
    /// 32 + 16 + 160 + 48 + 1 + 8 + 8 + 1 = 274
    pub const LEN: usize =
        32 + BRIDGE_KIND_LEN + BRIDGE_ENDPOINT_LEN + BRIDGE_LABEL_LEN + 1 + 8 + 8 + 1;
}

// ---------------------------------------------------------------------------
// v0.1.4 addition -- the on-chain bridge pulse. A `BridgePulse` account is a
// liveness + work oracle for a registered `Bridge`: the bridge's off-chain
// service stamps it so anyone can verify, from the chain alone, that the
// bridge is operating and how much work it has settled. Additive: it does not
// change the byte layout of any account above, so every existing account keeps
// deserializing unchanged (full backward compatibility).
// ---------------------------------------------------------------------------

/// On-chain liveness + work oracle for a registered Bridge. The bridge's
/// off-chain service (e.g. the JVM relayer) stamps this so anyone can verify,
/// from the chain alone, that the bridge is operating and how much it settled.
///
/// PDA: seeds `[b"pulse", bridge]` -- one pulse per bridge. Created on the first
/// `bridge_pulse` call and updated on every subsequent call. Only the bridge
/// `authority` may write (enforced via `has_one` on the bridge).
#[account]
pub struct BridgePulse {
    pub bridge: Pubkey,
    pub authority: Pubkey,
    pub last_ts: i64,
    pub pulse_count: u64,
    pub relayed_claims: u64,
    pub version: u32,
    pub bump: u8,
}

impl BridgePulse {
    /// Serialized length excluding the 8-byte discriminator.
    /// 32 + 32 + 8 + 8 + 8 + 4 + 1 = 93
    pub const LEN: usize = 32 + 32 + 8 + 8 + 8 + 4 + 1;
}

impl Sospeso {
    /// Lamports already paid out across all honoured claims.
    #[inline]
    pub fn lamports_disbursed(&self) -> u64 {
        self.lamports_total.saturating_sub(self.lamports_remaining)
    }

    /// Claims still available before the pool hits its `max_claims` cap.
    #[inline]
    pub fn claims_left(&self) -> u32 {
        self.max_claims.saturating_sub(self.claims_count)
    }

    /// True once the pool can no longer honour a claim because it is out of
    /// either lamports or claim slots (independent of expiry).
    #[inline]
    pub fn is_exhausted(&self) -> bool {
        self.lamports_remaining == 0 || self.claims_count >= self.max_claims
    }

    /// True if `now` is at or past the pool's expiry timestamp.
    #[inline]
    pub fn is_expired_at(&self, now: i64) -> bool {
        now >= self.expiry_ts
    }
}
