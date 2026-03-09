//! In-memory registry of sospesos and the claims drawn against them.
//!
//! This is the reference backend the relayer service mirrors with Postgres. It
//! owns the pools, records receipts, and enforces the double-claim guard by
//! keying receipts on `(sospeso_id, beneficiary)`.

use std::collections::{HashMap, HashSet};

use crate::claim::{apply_claim, evaluate_claim, ClaimReceipt, ClaimRequest};
use crate::error::{ProtocolError, Result};
use crate::types::{Pubkey, Sospeso, SospesoId};

/// Aggregate counters surfaced to a `/stats` endpoint.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct RegistryStats {
    /// Number of pools registered.
    pub total_sospesos: usize,
    /// Number of claims served.
    pub total_claims: usize,
    /// Total lamports drawn across all claims.
    pub total_lamports_drawn: u64,
}

/// A registry of sospesos plus their claim receipts.
#[derive(Debug, Default)]
pub struct Registry {
    pools: HashMap<SospesoId, Sospeso>,
    receipts: Vec<(SospesoId, ClaimReceipt)>,
    claimed_pairs: HashSet<(SospesoId, Pubkey)>,
}

impl Registry {
    /// Create an empty registry.
    pub fn new() -> Self {
        Registry::default()
    }

    /// Insert a pool, deriving its id from sponsor + claims_count seed, and
    /// return the assigned id. Ids are made unique by appending a counter on
    /// collision so repeated inserts from one sponsor never clobber.
    pub fn insert(&mut self, sospeso: Sospeso) -> SospesoId {
        let base = SospesoId::derive(&sospeso.params.sponsor, self.pools.len() as u64);
        let mut id = base.clone();
        let mut suffix = 1u64;
        while self.pools.contains_key(&id) {
            id = SospesoId::new(format!("{}_{}", base, suffix));
            suffix += 1;
        }
        self.pools.insert(id.clone(), sospeso);
        id
    }

    /// Insert a pool under a caller-chosen id, failing on collision.
    pub fn insert_with_id(&mut self, id: SospesoId, sospeso: Sospeso) -> Result<()> {
        if self.pools.contains_key(&id) {
            return Err(ProtocolError::AlreadyExists(id.to_string()));
        }
        self.pools.insert(id, sospeso);
        Ok(())
    }

    /// Borrow a pool by id.
    pub fn get(&self, id: &SospesoId) -> Option<&Sospeso> {
        self.pools.get(id)
    }

    /// Number of pools.
    pub fn len(&self) -> usize {
        self.pools.len()
    }

    /// Whether the registry holds no pools.
    pub fn is_empty(&self) -> bool {
        self.pools.is_empty()
    }

    /// All pools, sorted newest-first by creation time.
    pub fn list(&self) -> Vec<(&SospesoId, &Sospeso)> {
        let mut out: Vec<_> = self.pools.iter().collect();
        out.sort_by_key(|(_, pool)| core::cmp::Reverse(pool.created_at));
        out
    }

    /// Whether `beneficiary` already holds a receipt against `id`.
    pub fn has_claimed(&self, id: &SospesoId, beneficiary: &Pubkey) -> bool {
        self.claimed_pairs
            .contains(&(id.clone(), *beneficiary))
    }

    /// Validate and record a claim, debiting the pool. Returns the receipt.
    pub fn claim(
        &mut self,
        id: &SospesoId,
        request: ClaimRequest,
        now: i64,
    ) -> Result<ClaimReceipt> {
        let already = self.has_claimed(id, &request.beneficiary);
        let pool = self
            .pools
            .get_mut(id)
            .ok_or_else(|| ProtocolError::NotFound(id.to_string()))?;

        let receipt = evaluate_claim(pool, &request, now, already)?;
        apply_claim(pool, &receipt)?;

        self.claimed_pairs
            .insert((id.clone(), request.beneficiary));
        self.receipts.push((id.clone(), receipt.clone()));
        Ok(receipt)
    }

    /// Count receipts for a beneficiary at or after `since` (unix seconds).
    pub fn count_claims_for_wallet(&self, beneficiary: &Pubkey, since: i64) -> usize {
        self.receipts
            .iter()
            .filter(|(_, r)| &r.beneficiary == beneficiary && r.ts >= since)
            .count()
    }

    /// All receipts recorded against a given pool.
    pub fn receipts_for(&self, id: &SospesoId) -> Vec<&ClaimReceipt> {
        self.receipts
            .iter()
            .filter(|(pid, _)| pid == id)
            .map(|(_, r)| r)
            .collect()
    }

    /// Compute aggregate stats.
    pub fn stats(&self) -> RegistryStats {
        let total_lamports_drawn = self.receipts.iter().map(|(_, r)| r.amount).sum();
        RegistryStats {
            total_sospesos: self.pools.len(),
            total_claims: self.receipts.len(),
            total_lamports_drawn,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::SospesoParams;

    fn pk(s: u8) -> Pubkey {
        Pubkey::from_bytes([s; 32])
    }

    fn registry_with_pool() -> (Registry, SospesoId) {
        let mut r = Registry::new();
        let id = r.insert(Sospeso::open(
            SospesoParams::new(pk(1), 1_000).with_max_per_claim(300),
            0,
        ));
        (r, id)
    }

    #[test]
    fn insert_and_get() {
        let (r, id) = registry_with_pool();
        assert_eq!(r.len(), 1);
        assert_eq!(r.get(&id).unwrap().lamports_remaining, 1_000);
    }

    #[test]
    fn claim_debits_and_guards_double() {
        let (mut r, id) = registry_with_pool();
        let receipt = r.claim(&id, ClaimRequest::new(pk(2), 250), 10).unwrap();
        assert_eq!(receipt.amount, 250);
        assert_eq!(r.get(&id).unwrap().lamports_remaining, 750);
        // Second claim by the same wallet is rejected.
        assert!(matches!(
            r.claim(&id, ClaimRequest::new(pk(2), 100), 11),
            Err(ProtocolError::DoubleClaim)
        ));
    }

    #[test]
    fn stats_aggregate() {
        let (mut r, id) = registry_with_pool();
        r.claim(&id, ClaimRequest::new(pk(2), 100), 10).unwrap();
        r.claim(&id, ClaimRequest::new(pk(3), 150), 10).unwrap();
        let s = r.stats();
        assert_eq!(s.total_claims, 2);
        assert_eq!(s.total_lamports_drawn, 250);
        assert_eq!(s.total_sospesos, 1);
    }

    #[test]
    fn unknown_pool_is_not_found() {
        let mut r = Registry::new();
        let missing = SospesoId::new("nope");
        assert!(matches!(
            r.claim(&missing, ClaimRequest::new(pk(2), 10), 0),
            Err(ProtocolError::NotFound(_))
        ));
    }

    #[test]
    fn explicit_id_collision() {
        let mut r = Registry::new();
        let id = SospesoId::new("fixed");
        r.insert_with_id(id.clone(), Sospeso::open(SospesoParams::new(pk(1), 1), 0))
            .unwrap();
        assert!(matches!(
            r.insert_with_id(id, Sospeso::open(SospesoParams::new(pk(1), 1), 0)),
            Err(ProtocolError::AlreadyExists(_))
        ));
    }
}
