//! The matching engine: pick the best eligible sospeso for a beneficiary.
//!
//! Given a beneficiary and how many lamports they need, the matcher scans the
//! registry for pools that pass both the static budget checks and the
//! eligibility rules, then ranks them. The default policy prefers the pool with
//! the *smallest* remaining budget that still covers the request -- spend the
//! change jar before the big fund -- breaking ties on nearest expiry so
//! soon-to-expire pools are used first.

use crate::eligibility::{check_eligibility, EligibilityPolicy, WalletProfile};
use crate::registry::Registry;
use crate::types::{Pubkey, Sospeso, SospesoId};

/// What a beneficiary is asking the matcher for.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct MatchCriteria {
    /// The wallet seeking sponsorship.
    pub beneficiary: Pubkey,
    /// Lamports the caller expects to need.
    pub needed_lamports: u64,
    /// Program the transaction targets (default = any).
    pub program: Pubkey,
}

impl MatchCriteria {
    /// Build criteria for a beneficiary needing `needed_lamports`.
    pub fn new(beneficiary: Pubkey, needed_lamports: u64) -> Self {
        MatchCriteria {
            beneficiary,
            needed_lamports,
            program: Pubkey::default_key(),
        }
    }

    /// Restrict matching to a specific program (builder style).
    pub fn for_program(mut self, program: Pubkey) -> Self {
        self.program = program;
        self
    }
}

/// A ranked match result.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct MatchOutcome {
    /// The chosen pool's id.
    pub id: SospesoId,
    /// The pool's remaining budget at match time.
    pub remaining: u64,
    /// Whether the pool was new-wallet-only.
    pub new_wallet_only: bool,
}

/// Scans a registry and selects the best eligible pool.
#[derive(Clone, Copy, Debug, Default)]
pub struct Matcher {
    policy: EligibilityPolicy,
}

impl Matcher {
    /// Build a matcher with a custom eligibility policy.
    pub fn with_policy(policy: EligibilityPolicy) -> Self {
        Matcher { policy }
    }

    /// Whether a single pool both covers the request and passes eligibility.
    pub fn is_eligible(
        &self,
        registry: &Registry,
        id: &SospesoId,
        pool: &Sospeso,
        criteria: &MatchCriteria,
        profile: &WalletProfile,
        now: i64,
    ) -> bool {
        if !pool.can_cover(criteria.needed_lamports, now) {
            return false;
        }
        if registry.has_claimed(id, &criteria.beneficiary) {
            return false;
        }
        check_eligibility(
            pool,
            &criteria.beneficiary,
            profile,
            &criteria.program,
            now,
            &self.policy,
        )
        .is_ok()
    }

    /// Find the best eligible pool, or `None` when nothing fits.
    pub fn find(
        &self,
        registry: &Registry,
        criteria: &MatchCriteria,
        profile: &WalletProfile,
        now: i64,
    ) -> Option<MatchOutcome> {
        let mut best: Option<(&SospesoId, &Sospeso)> = None;
        for (id, pool) in registry.list() {
            if !self.is_eligible(registry, id, pool, criteria, profile, now) {
                continue;
            }
            best = Some(match best {
                None => (id, pool),
                Some(current) => {
                    if Self::prefers(pool, current.1) {
                        (id, pool)
                    } else {
