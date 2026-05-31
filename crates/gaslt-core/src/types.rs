//! Core domain types: addresses, pool parameters, and the sospeso record.
//!
//! `Pubkey` is a thin 32-byte newtype rather than a dependency on a specific
//! chain SDK, keeping this crate light and portable. The on-chain program and
//! the relayer convert to/from their native key types at the boundary.

use borsh::{BorshDeserialize, BorshSerialize};

/// A 32-byte account address (an ed25519 public key on Solana).
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, BorshSerialize, BorshDeserialize)]
pub struct Pubkey([u8; 32]);

impl Pubkey {
    /// Wrap raw bytes as a public key.
    pub const fn from_bytes(bytes: [u8; 32]) -> Self {
        Pubkey(bytes)
    }

    /// The underlying bytes.
    pub const fn to_bytes(&self) -> [u8; 32] {
        self.0
    }

    /// The all-zero address (the System Program / "default" key).
    pub const fn default_key() -> Self {
        Pubkey([0u8; 32])
    }

    /// Whether this is the all-zero address.
    pub fn is_default(&self) -> bool {
        self.0 == [0u8; 32]
    }

    /// A short, log-friendly hex fragment such as `0102..1f20`.
    pub fn short(&self) -> String {
        let b = &self.0;
        format!("{:02x}{:02x}..{:02x}{:02x}", b[0], b[1], b[30], b[31])
    }
}

impl core::fmt::Debug for Pubkey {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "Pubkey({})", self.short())
    }
}

/// A registry-level identifier for a sospeso (distinct from its on-chain PDA).
#[derive(Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Debug, BorshSerialize, BorshDeserialize)]
pub struct SospesoId(String);

impl SospesoId {
    /// Build an id from any string-like value.
    pub fn new(value: impl Into<String>) -> Self {
        SospesoId(value.into())
    }

    /// Borrow the inner string.
    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// Derive a deterministic id from a sponsor and nonce (mirrors PDA seeds).
    pub fn derive(sponsor: &Pubkey, nonce: u64) -> Self {
        SospesoId(format!("spo_{}_{}", sponsor.short(), nonce))
    }
}

impl core::fmt::Display for SospesoId {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.write_str(&self.0)
    }
}

/// Parameters supplied by a sponsor when opening a sospeso.
#[derive(Clone, Debug, PartialEq, Eq, BorshSerialize, BorshDeserialize)]
pub struct SospesoParams {
    /// Wallet funding the escrow.
    pub sponsor: Pubkey,
    /// Program the sponsored transactions are expected to target (default = any).
    pub program: Pubkey,
    /// Total lamports committed.
    pub lamports_total: u64,
    /// Maximum lamports any single claim may draw (0 = no per-claim cap).
    pub max_per_claim: u64,
    /// Maximum number of claims in total (0 = unbounded by count).
    pub max_claims: u32,
    /// Unix timestamp after which the pool expires (0 = never).
    pub expiry_ts: i64,
    /// When true, only wallets passing the new-wallet heuristic may claim.
    pub new_wallet_only: bool,
}

impl SospesoParams {
    /// Start a parameter set with the required fields; the rest default to open.
    pub fn new(sponsor: Pubkey, lamports_total: u64) -> Self {
        SospesoParams {
            sponsor,
            program: Pubkey::default_key(),
            lamports_total,
            max_per_claim: 0,
            max_claims: 0,
            expiry_ts: 0,
            new_wallet_only: false,
        }
    }

    /// Set the per-claim ceiling (builder style).
    pub fn with_max_per_claim(mut self, cap: u64) -> Self {
        self.max_per_claim = cap;
        self
    }

    /// Set the total claim count limit (builder style).
    pub fn with_max_claims(mut self, max: u32) -> Self {
        self.max_claims = max;
        self
    }

    /// Set the expiry timestamp (builder style).
    pub fn with_expiry(mut self, ts: i64) -> Self {
        self.expiry_ts = ts;
        self
    }

    /// Restrict the pool to a specific target program (builder style).
    pub fn for_program(mut self, program: Pubkey) -> Self {
        self.program = program;
        self
    }

    /// Mark the pool as new-wallet-only (builder style).
    pub fn new_wallets_only(mut self) -> Self {
        self.new_wallet_only = true;
        self
    }
}

/// A live sospeso: the sponsor's parameters plus mutable accounting state.
#[derive(Clone, Debug, PartialEq, Eq, BorshSerialize, BorshDeserialize)]
pub struct Sospeso {
    /// The fixed parameters set at creation.
    pub params: SospesoParams,
    /// Lamports still available to claim.
    pub lamports_remaining: u64,
    /// Number of claims served so far.
    pub claims_count: u32,
    /// Creation timestamp (unix seconds).
    pub created_at: i64,
}

impl Sospeso {
    /// Open a sospeso from parameters at `created_at`, fully funded.
    pub fn open(params: SospesoParams, created_at: i64) -> Self {
        let lamports_remaining = params.lamports_total;
        Sospeso {
            params,
            lamports_remaining,
            claims_count: 0,
            created_at,
        }
    }

    /// Whether the pool has expired at `now` (expiry 0 means never).
    pub fn is_expired(&self, now: i64) -> bool {
        self.params.expiry_ts != 0 && self.params.expiry_ts <= now
    }

    /// Whether the pool has exhausted its allowed number of claims.
    pub fn claims_exhausted(&self) -> bool {
        self.params.max_claims != 0 && self.claims_count >= self.params.max_claims
    }

    /// Whether the pool can still cover a claim of `amount` lamports right now.
    pub fn can_cover(&self, amount: u64, now: i64) -> bool {
        amount > 0
            && !self.is_expired(now)
            && !self.claims_exhausted()
            && self.lamports_remaining >= amount
            && (self.params.max_per_claim == 0 || amount <= self.params.max_per_claim)
    }

    /// Fraction of the original budget already spent, in basis points (0-10000).
    pub fn utilization_bps(&self) -> u32 {
        let total = self.params.lamports_total;
        if total == 0 {
            return 0;
        }
        let spent = total.saturating_sub(self.lamports_remaining);
        ((spent as u128 * 10_000) / total as u128) as u32
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pk(seed: u8) -> Pubkey {
        Pubkey::from_bytes([seed; 32])
    }

    #[test]
    fn builder_sets_fields() {
        let p = SospesoParams::new(pk(1), 1_000)
            .with_max_per_claim(100)
            .with_max_claims(5)
            .with_expiry(42)
            .new_wallets_only();
        assert_eq!(p.max_per_claim, 100);
        assert_eq!(p.max_claims, 5);
        assert_eq!(p.expiry_ts, 42);
        assert!(p.new_wallet_only);
    }

    #[test]
    fn can_cover_respects_caps_and_expiry() {
        let s = Sospeso::open(
            SospesoParams::new(pk(1), 1_000)
                .with_max_per_claim(300)
                .with_expiry(100),
            0,
        );
        assert!(s.can_cover(300, 50));
        assert!(!s.can_cover(301, 50)); // over per-claim cap
        assert!(!s.can_cover(300, 100)); // expired
        assert!(!s.can_cover(0, 50)); // zero amount
    }

    #[test]
    fn utilization_tracks_spend() {
        let mut s = Sospeso::open(SospesoParams::new(pk(1), 1_000), 0);
        assert_eq!(s.utilization_bps(), 0);
        s.lamports_remaining = 250;
        assert_eq!(s.utilization_bps(), 7_500);
    }

    #[test]
    fn id_derivation_is_deterministic() {
        let a = SospesoId::derive(&pk(7), 3);
        let b = SospesoId::derive(&pk(7), 3);
        assert_eq!(a, b);
        assert!(a.as_str().starts_with("spo_"));
    }

    #[test]
    fn roundtrips_through_borsh() {
        let s = Sospeso::open(SospesoParams::new(pk(2), 5_000).with_max_claims(9), 11);
        let bytes = borsh::to_vec(&s).unwrap();
        let back: Sospeso = borsh::from_slice(&bytes).unwrap();
        assert_eq!(s, back);
    }
}
