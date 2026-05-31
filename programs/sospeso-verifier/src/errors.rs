//! Custom error codes for sospeso_verifier.

use anchor_lang::prelude::*;

#[error_code]
pub enum SospesoError {
    #[msg("The sospeso pool has expired")]
    Expired,
    #[msg("The pool has reached its maximum number of claims")]
    ClaimCapReached,
    #[msg("Not enough lamports remaining in the pool")]
    InsufficientFunds,
    #[msg("Requested amount exceeds the per-claim cap (or the initial deposit)")]
    AmountTooLarge,
    #[msg("The pool has not expired yet; reclaim is unavailable")]
    NotExpiredYet,
    #[msg("Signer is not authorized for this sospeso")]
    Unauthorized,
    #[msg("Amount must be greater than zero")]
    ZeroAmount,
    #[msg("max_claims must be greater than zero")]
    ZeroMaxClaims,
    #[msg("max_per_claim must be greater than zero")]
    ZeroMaxPerClaim,
    #[msg("Arithmetic overflow")]
    Overflow,
    // --- v0.1.2 additions ---
    #[msg("The label is empty")]
    LabelEmpty,
    #[msg("The label exceeds the maximum length")]
    LabelTooLong,
    #[msg("The description exceeds the maximum length")]
    DescriptionTooLong,
    #[msg("The URI exceeds the maximum length")]
    UriTooLong,
    #[msg("The category value is out of range")]
    InvalidCategory,
    #[msg("The new expiry must be in the future")]
    ExpiryNotInFuture,
    #[msg("The new expiry must be later than the current expiry")]
    ExpiryNotExtended,
    #[msg("The provided sospeso does not match this record")]
    SospesoMismatch,
    #[msg("The provided registry does not match this entry")]
    RegistryMismatch,
}
