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
