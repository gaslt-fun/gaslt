//! # gaslt-core
//!
//! Core logic for the **sospeso** gas-abstraction protocol.
//!
//! A *sospeso* (from the Neapolitan *caffè sospeso* -- a "suspended coffee" paid
//! forward for a stranger) is a pool of lamports a sponsor pre-pays so that other
//! wallets can transact without holding SOL. A relayer acts as the transaction
//! fee payer (the Octane pattern) and reimburses itself from the matched pool.
//!
//! This crate is the chain-agnostic reference implementation of the off-chain
//! accounting and decision rules that mirror the on-chain `sospeso_verifier`
//! program:
//!
//! - [`registry`] -- in-memory store of sospesos and the claims drawn against them.
//! - [`escrow`] -- checked lamport accounting with a rent-exempt floor.
//! - [`claim`] -- claim verification (budget, per-claim cap, expiry, double-claim).
//! - [`eligibility`] -- new-wallet gating and program-id matching.
//! - [`rate`] -- fixed-window rate limiting along the ip / wallet / sospeso axes.
//! - [`matching`] -- selecting the best eligible sospeso for a beneficiary.
//!
//! Everything here is deterministic and side-effect free: callers inject the
//! current time and any wallet metadata, so the rules can be unit-tested and
//! reused on-chain, in the relayer service, or in the SDK.
//!
//! ```
//! use gaslt_core::prelude::*;
//!
//! let mut registry = Registry::new();
//! let sponsor = Pubkey::from_bytes([1u8; 32]);
//! let params = SospesoParams::new(sponsor, 2_000_000)
//!     .with_max_per_claim(50_000)
//!     .with_max_claims(40);
//! let id = registry.insert(Sospeso::open(params, 0));
//!
//! let beneficiary = Pubkey::from_bytes([9u8; 32]);
//! let receipt = registry
//!     .claim(&id, ClaimRequest::new(beneficiary, 40_000), 10)
//!     .expect("a fresh pool covers the first claim");
//! assert_eq!(receipt.amount, 40_000);
//! ```

#![forbid(unsafe_code)]
#![deny(missing_debug_implementations)]

pub mod claim;
pub mod eligibility;
pub mod error;
pub mod escrow;
pub mod matching;
pub mod rate;
pub mod registry;
pub mod types;

pub use error::{ProtocolError, Result};

/// The protocol version this crate implements, surfaced by the SDK and CLI.
pub const PROTOCOL_VERSION: &str = "0.4.2";

/// Default rent-exempt floor (lamports) kept in a sospeso escrow so the account
/// is never debited below the threshold that would let the runtime reap it.
pub const DEFAULT_RENT_FLOOR: u64 = 890_880;

/// Convenience re-exports for the common path of building and drawing on pools.
pub mod prelude {
    pub use crate::claim::{ClaimReceipt, ClaimRequest};
    pub use crate::eligibility::{EligibilityPolicy, WalletProfile};
    pub use crate::error::{ProtocolError, Result};
    pub use crate::escrow::Escrow;
    pub use crate::matching::{MatchCriteria, Matcher};
    pub use crate::rate::{RateDecision, RateLimiter, RateRule};
    pub use crate::registry::Registry;
    pub use crate::types::{Pubkey, Sospeso, SospesoParams, SospesoId};
}
