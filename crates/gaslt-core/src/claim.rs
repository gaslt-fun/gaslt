//! Claim verification.
//!
//! A claim draws lamports from a sospeso to reimburse the relayer for the fee it
//! fronted on a beneficiary's transaction. [`evaluate_claim`] applies every rule
//! the on-chain `claim_sospeso` instruction would and, on success, returns the
//! [`ClaimReceipt`] that the registry persists. The receipt's existence is the
//! double-claim guard, exactly like the on-chain `ClaimReceipt` PDA.

use borsh::{BorshDeserialize, BorshSerialize};

use crate::error::{ProtocolError, Result};
use crate::types::{Pubkey, Sospeso};

/// A request to draw `amount` lamports on behalf of `beneficiary`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ClaimRequest {
    /// Wallet the claim is credited to (and keyed on for double-claim checks).
    pub beneficiary: Pubkey,
    /// Lamports requested (typically the fee the relayer fronted).
    pub amount: u64,
}

impl ClaimRequest {
    /// Build a claim request.
    pub fn new(beneficiary: Pubkey, amount: u64) -> Self {
        ClaimRequest {
            beneficiary,
            amount,
        }
    }
}

/// The record produced by a successful claim.
#[derive(Clone, Debug, PartialEq, Eq, BorshSerialize, BorshDeserialize)]
pub struct ClaimReceipt {
    /// Wallet credited.
    pub beneficiary: Pubkey,
    /// Lamports drawn.
    pub amount: u64,
    /// Timestamp the claim was served.
    pub ts: i64,
}

impl ClaimReceipt {
    /// Construct a receipt.
    pub fn new(beneficiary: Pubkey, amount: u64, ts: i64) -> Self {
        ClaimReceipt {
            beneficiary,
            amount,
            ts,
        }
    }
}

/// Validate a claim against a pool's current state.
///
/// `already_claimed` is whether a receipt for this (pool, beneficiary) pair
/// already exists. The function is pure: it mutates nothing and returns the
/// receipt to apply on success. Order of checks is deliberate -- cheapest and
/// most-permanent rejections first -- so callers get the most useful error.
pub fn evaluate_claim(
    sospeso: &Sospeso,
    request: &ClaimRequest,
    now: i64,
    already_claimed: bool,
) -> Result<ClaimReceipt> {
    if request.amount == 0 {
        return Err(ProtocolError::InvalidAmount(0));
    }
    if already_claimed {
        return Err(ProtocolError::DoubleClaim);
    }
    if sospeso.is_expired(now) {
        return Err(ProtocolError::Expired {
            expiry: sospeso.params.expiry_ts,
            now,
        });
    }
    if sospeso.claims_exhausted() {
        return Err(ProtocolError::ClaimCountExhausted {
            count: sospeso.claims_count,
            max: sospeso.params.max_claims,
        });
    }
    let cap = sospeso.params.max_per_claim;
    if cap != 0 && request.amount > cap {
        return Err(ProtocolError::PerClaimCapExceeded {
            requested: request.amount,
            cap,
