//! Protocol error type shared by every decision rule.
//!
//! The variants intentionally line up with the on-chain program's error codes so
//! a relayer can map an off-chain pre-check rejection to the exact reason the
//! chain would have given, without round-tripping a transaction.

use thiserror::Error;

/// Errors produced while validating sospeso operations.
#[derive(Error, Debug, Clone, PartialEq, Eq)]
pub enum ProtocolError {
    /// The pool does not have enough remaining lamports for the request.
    #[error("insufficient pool budget: need {needed} lamports, {remaining} remaining")]
    InsufficientBudget { needed: u64, remaining: u64 },

    /// Debiting would push the escrow below its rent-exempt floor.
    #[error("escrow would fall below rent floor: balance {balance}, floor {floor}")]
    BelowRentFloor { balance: u64, floor: u64 },

    /// The per-claim ceiling would be exceeded by this request.
    #[error("per-claim cap exceeded: requested {requested}, cap {cap}")]
    PerClaimCapExceeded { requested: u64, cap: u64 },

    /// The pool has already served its maximum number of claims.
    #[error("claim count exhausted: {count}/{max} claims used")]
    ClaimCountExhausted { count: u32, max: u32 },

    /// The sospeso has passed its expiry timestamp.
    #[error("sospeso expired at {expiry}, now {now}")]
    Expired { expiry: i64, now: i64 },

    /// A receipt already exists for this (sospeso, beneficiary) pair.
    #[error("beneficiary has already claimed from this sospeso")]
    DoubleClaim,

    /// The pool is new-wallet-only and the beneficiary did not pass the check.
    #[error("sospeso is new-wallet-only and the beneficiary is not a new wallet")]
    NotNewWallet,

    /// The pool targets a different program than the request asked for.
    #[error("program mismatch: pool {pool}, requested {requested}")]
    ProgramMismatch { pool: String, requested: String },

    /// A rate-limit window has been exhausted.
    #[error("rate limited on {axis}: {count}/{limit} in window")]
    RateLimited {
        axis: &'static str,
        count: u32,
        limit: u32,
    },

    /// A requested sospeso id is not present in the registry.
    #[error("sospeso not found: {0}")]
    NotFound(String),

    /// An id collision occurred while inserting.
    #[error("sospeso already exists: {0}")]
    AlreadyExists(String),

    /// A zero or otherwise nonsensical amount was supplied.
    #[error("invalid amount: {0}")]
    InvalidAmount(u64),
