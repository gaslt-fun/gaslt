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
                        current
                    }
                }
            });
        }
        best.map(|(id, pool)| MatchOutcome {
            id: id.clone(),
            remaining: pool.lamports_remaining,
            new_wallet_only: pool.params.new_wallet_only,
        })
    }

    /// Ranking predicate: `candidate` beats `incumbent` when it has the smaller
    /// remaining budget, breaking ties toward the nearer expiry.
    fn prefers(candidate: &Sospeso, incumbent: &Sospeso) -> bool {
        if candidate.lamports_remaining != incumbent.lamports_remaining {
            return candidate.lamports_remaining < incumbent.lamports_remaining;
        }
        let exp = |s: &Sospeso| {
            if s.params.expiry_ts == 0 {
                i64::MAX
            } else {
                s.params.expiry_ts
            }
        };
        exp(candidate) < exp(incumbent)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::SospesoParams;

    fn pk(s: u8) -> Pubkey {
        Pubkey::from_bytes([s; 32])
    }

    #[test]
    fn picks_smallest_covering_pool() {
        let mut r = Registry::new();
        let big = r.insert(Sospeso::open(SospesoParams::new(pk(1), 5_000), 1));
        let small = r.insert(Sospeso::open(SospesoParams::new(pk(2), 1_000), 2));
        let _ = big;

        let m = Matcher::default();
        let out = m
            .find(
                &r,
                &MatchCriteria::new(pk(9), 400),
                &WalletProfile::verified(0),
                10,
            )
            .unwrap();
        assert_eq!(out.id, small);
    }

    #[test]
    fn skips_new_wallet_only_for_active_wallet() {
        let mut r = Registry::new();
        let open = r.insert(Sospeso::open(SospesoParams::new(pk(1), 2_000), 1));
        let _gated = r.insert(Sospeso::open(
            SospesoParams::new(pk(2), 1_000).new_wallets_only(),
            2,
        ));

        let m = Matcher::default();
        let out = m
            .find(
                &r,
                &MatchCriteria::new(pk(9), 400),
                &WalletProfile::verified(99), // active wallet
                10,
            )
            .unwrap();
        // The gated (smaller) pool is skipped; the open pool wins.
        assert_eq!(out.id, open);
    }

    #[test]
    fn none_when_budget_insufficient() {
        let mut r = Registry::new();
        r.insert(Sospeso::open(SospesoParams::new(pk(1), 100), 1));
        let m = Matcher::default();
        assert!(m
            .find(
                &r,
                &MatchCriteria::new(pk(9), 400),
                &WalletProfile::verified(0),
                10
            )
            .is_none());
    }
}
