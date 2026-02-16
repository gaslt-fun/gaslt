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
            });
        }
        self.balance = post;
        Ok(self.balance)
    }

    /// Reclaim everything above the rent floor (sponsor withdrawal after expiry).
    /// Returns the amount swept; the escrow is left holding exactly the floor.
    pub fn sweep_claimable(&mut self) -> u64 {
        let swept = self.claimable();
        self.balance -= swept;
        swept
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn claimable_excludes_floor() {
        let e = Escrow::with_rent_floor(1_000, 200);
        assert_eq!(e.claimable(), 800);
        assert!(e.can_debit(800));
        assert!(!e.can_debit(801));
    }

    #[test]
    fn debit_protects_floor() {
        let mut e = Escrow::with_rent_floor(1_000, 200);
        assert_eq!(e.debit(500).unwrap(), 500);
        let err = e.debit(400).unwrap_err();
        assert!(matches!(err, ProtocolError::BelowRentFloor { .. }));
        assert_eq!(e.balance(), 500);
    }

    #[test]
    fn deposit_and_sweep() {
        let mut e = Escrow::with_rent_floor(500, 200);
        e.deposit(300).unwrap();
        assert_eq!(e.balance(), 800);
        let swept = e.sweep_claimable();
        assert_eq!(swept, 600);
        assert_eq!(e.balance(), 200);
    }

    #[test]
    fn zero_amounts_rejected() {
        let mut e = Escrow::new(1_000_000);
        assert!(matches!(e.deposit(0), Err(ProtocolError::InvalidAmount(0))));
        assert!(matches!(e.debit(0), Err(ProtocolError::InvalidAmount(0))));
    }
}
