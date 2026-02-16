//! Lamport accounting for a sospeso escrow.
//!
//! The escrow holds the sponsor's lamports and is debited as claims are served.
//! Every mutation is checked: it never overflows and never drops the balance
//! below the rent-exempt floor, which mirrors the on-chain invariant that a
//! data-carrying PDA must stay rent-exempt to survive.

use crate::error::{ProtocolError, Result};
use crate::DEFAULT_RENT_FLOOR;

/// A checked lamport balance with a protected rent floor.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Escrow {
    balance: u64,
    rent_floor: u64,
}

impl Escrow {
    /// Create an escrow seeded with `balance` lamports and the default floor.
    pub fn new(balance: u64) -> Self {
        Escrow {
            balance,
            rent_floor: DEFAULT_RENT_FLOOR,
        }
    }

    /// Create an escrow with an explicit rent floor.
    pub fn with_rent_floor(balance: u64, rent_floor: u64) -> Self {
        Escrow { balance, rent_floor }
    }

    /// Current total balance (including the protected floor).
    pub fn balance(&self) -> u64 {
        self.balance
    }

    /// The rent-exempt floor that may never be debited.
    pub fn rent_floor(&self) -> u64 {
        self.rent_floor
    }

    /// Lamports actually available to claim (balance above the floor).
    pub fn claimable(&self) -> u64 {
        self.balance.saturating_sub(self.rent_floor)
    }

    /// Whether at least `amount` lamports can be debited without breaching the floor.
    pub fn can_debit(&self, amount: u64) -> bool {
        amount > 0 && self.claimable() >= amount
    }

    /// Add lamports to the escrow (a sponsor top-up). Checked for overflow.
    pub fn deposit(&mut self, amount: u64) -> Result<u64> {
        if amount == 0 {
            return Err(ProtocolError::InvalidAmount(0));
        }
        self.balance = self
            .balance
            .checked_add(amount)
            .ok_or(ProtocolError::Overflow)?;
        Ok(self.balance)
    }

    /// Remove `amount` lamports, refusing if it would breach the rent floor.
    pub fn debit(&mut self, amount: u64) -> Result<u64> {
        if amount == 0 {
            return Err(ProtocolError::InvalidAmount(0));
        }
        let post = self
            .balance
            .checked_sub(amount)
            .ok_or(ProtocolError::Overflow)?;
        if post < self.rent_floor {
            return Err(ProtocolError::BelowRentFloor {
                balance: self.balance,
                floor: self.rent_floor,
