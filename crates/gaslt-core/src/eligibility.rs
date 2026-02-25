//! Beneficiary eligibility: new-wallet gating and program matching.
//!
//! Sponsors can restrict a pool to "new" wallets (onboarding funds) and/or to a
//! single target program. The new-wallet test is a heuristic over a wallet's
//! historical activity, supplied by the caller as a [`WalletProfile`] so this
//! crate never has to talk to an RPC.

use crate::error::{ProtocolError, Result};
use crate::types::{Pubkey, Sospeso};

/// Activity snapshot for a wallet, gathered by the caller (e.g. via RPC).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct WalletProfile {
    /// Number of historical signatures observed (capped is fine).
    pub signature_count: u32,
    /// Whether the profile was actually fetched (vs. an unverifiable default).
    pub verified: bool,
}

impl WalletProfile {
    /// A verified profile with `signature_count` signatures.
    pub fn verified(signature_count: u32) -> Self {
        WalletProfile {
            signature_count,
            verified: true,
        }
    }

    /// An unverified profile (the RPC check failed or was skipped).
    pub fn unverified() -> Self {
        WalletProfile {
            signature_count: u32::MAX,
            verified: false,
        }
    }
}

/// Policy thresholds for eligibility checks.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct EligibilityPolicy {
    /// At or below this signature count a wallet counts as "new".
    pub new_wallet_max_signatures: u32,
    /// When true, an unverifiable profile fails new-wallet gating (fail-closed).
    pub require_verified_for_new: bool,
}

impl Default for EligibilityPolicy {
    fn default() -> Self {
        EligibilityPolicy {
            new_wallet_max_signatures: 5,
            require_verified_for_new: true,
        }
    }
}

impl EligibilityPolicy {
    /// Decide whether a profile qualifies as a new wallet under this policy.
    pub fn is_new_wallet(&self, profile: &WalletProfile) -> bool {
        if !profile.verified {
            // Fail-closed: an unverifiable wallet is not treated as new.
            return !self.require_verified_for_new;
        }
        profile.signature_count <= self.new_wallet_max_signatures
    }
}

/// Check that `beneficiary` (described by `profile`) may claim from `sospeso`
/// for `program`, at time `now`. Returns `Ok(())` when eligible.
pub fn check_eligibility(
    sospeso: &Sospeso,
    beneficiary: &Pubkey,
    profile: &WalletProfile,
    program: &Pubkey,
    now: i64,
    policy: &EligibilityPolicy,
) -> Result<()> {
    // A beneficiary may not sponsor itself out of its own pool.
    if &sospeso.params.sponsor == beneficiary {
        return Err(ProtocolError::NotNewWallet);
    }
    if sospeso.is_expired(now) {
        return Err(ProtocolError::Expired {
            expiry: sospeso.params.expiry_ts,
            now,
        });
    }
    // Program restriction: a default (zero) pool program matches anything.
    if !sospeso.params.program.is_default() && &sospeso.params.program != program {
        return Err(ProtocolError::ProgramMismatch {
