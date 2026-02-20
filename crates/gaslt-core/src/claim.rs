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
        });
    }
    if sospeso.lamports_remaining < request.amount {
        return Err(ProtocolError::InsufficientBudget {
            needed: request.amount,
            remaining: sospeso.lamports_remaining,
        });
    }
    Ok(ClaimReceipt::new(request.beneficiary, request.amount, now))
}

/// Apply a validated receipt to a pool, debiting it and bumping the counter.
///
/// Returns the pool's remaining lamports after the debit. This is the only
/// function in the module that mutates, and it assumes [`evaluate_claim`] has
/// already accepted the request (it still guards against underflow).
pub fn apply_claim(sospeso: &mut Sospeso, receipt: &ClaimReceipt) -> Result<u64> {
    sospeso.lamports_remaining = sospeso
        .lamports_remaining
        .checked_sub(receipt.amount)
        .ok_or(ProtocolError::Overflow)?;
    sospeso.claims_count = sospeso
        .claims_count
        .checked_add(1)
        .ok_or(ProtocolError::Overflow)?;
    Ok(sospeso.lamports_remaining)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::SospesoParams;

    fn pool() -> Sospeso {
        Sospeso::open(
            SospesoParams::new(Pubkey::from_bytes([1; 32]), 1_000)
                .with_max_per_claim(300)
                .with_max_claims(3)
                .with_expiry(1_000),
            0,
        )
    }

    fn req(amount: u64) -> ClaimRequest {
        ClaimRequest::new(Pubkey::from_bytes([2; 32]), amount)
    }

    #[test]
    fn happy_path_and_apply() {
        let mut p = pool();
        let receipt = evaluate_claim(&p, &req(250), 10, false).unwrap();
        let remaining = apply_claim(&mut p, &receipt).unwrap();
        assert_eq!(remaining, 750);
        assert_eq!(p.claims_count, 1);
    }

    #[test]
    fn double_claim_blocked() {
        let p = pool();
        assert!(matches!(
            evaluate_claim(&p, &req(100), 10, true),
            Err(ProtocolError::DoubleClaim)
        ));
    }

    #[test]
    fn per_claim_cap_enforced() {
        let p = pool();
        assert!(matches!(
            evaluate_claim(&p, &req(301), 10, false),
            Err(ProtocolError::PerClaimCapExceeded { .. })
        ));
    }

    #[test]
    fn expiry_enforced() {
        let p = pool();
        assert!(matches!(
            evaluate_claim(&p, &req(100), 1_000, false),
            Err(ProtocolError::Expired { .. })
        ));
    }

    #[test]
    fn count_exhaustion_enforced() {
        let mut p = pool();
        p.claims_count = 3;
        assert!(matches!(
            evaluate_claim(&p, &req(100), 10, false),
            Err(ProtocolError::ClaimCountExhausted { .. })
        ));
    }

    #[test]
    fn budget_shortfall_enforced() {
        let mut p = pool();
        p.lamports_remaining = 50;
        assert!(matches!(
            evaluate_claim(&p, &req(100), 10, false),
            Err(ProtocolError::InsufficientBudget { .. })
        ));
    }
}
