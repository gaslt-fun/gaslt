//! Core domain types: addresses, pool parameters, and the sospeso record.
//!
//! `Pubkey` is a thin 32-byte newtype rather than a dependency on a specific
//! chain SDK, keeping this crate light and portable. The on-chain program and
//! the relayer convert to/from their native key types at the boundary.

use borsh::{BorshDeserialize, BorshSerialize};

/// A 32-byte account address (an ed25519 public key on Solana).
#[derive(
    Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, BorshSerialize, BorshDeserialize,
)]
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
        format!(
            "{:02x}{:02x}..{:02x}{:02x}",
            b[0], b[1], b[30], b[31]
        )
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
