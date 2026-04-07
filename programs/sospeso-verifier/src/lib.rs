//! # sospeso_verifier
//!
//! On-chain escrow and claim accounting for the sospeso gas-abstraction
//! protocol. A sponsor opens a pool (`create_sospeso`), beneficiaries -- or a
//! relayer paying rent on their behalf -- draw lamports from it (`claim_sospeso`),
//! sponsors may add funds (`top_up`), and after expiry the sponsor sweeps the
//! remainder (`reclaim`).
//!
//! Lamports are moved by directly mutating account balances rather than via a
//! System transfer, because a data-carrying PDA cannot be the source of a
//! System transfer. The escrow always stays rent-exempt: only the claimable
//! `lamports_remaining` is ever debited.

use anchor_lang::prelude::*;

pub mod errors;
pub mod state;

use crate::errors::SospesoError;
use crate::state::{ClaimReceipt, Sospeso};

declare_id!("CKepu1fT4aCkDrsWLv499WCVMb5KKVfG5a1znjC5yuYA");

#[program]
pub mod sospeso_verifier {
    use super::*;

    /// Open a sospeso, moving `amount` lamports from the sponsor into the PDA.
    pub fn create_sospeso(
        ctx: Context<CreateSospeso>,
        nonce: u64,
        amount: u64,
        max_per_claim: u64,
        max_claims: u32,
        expiry_ts: i64,
        new_wallet_only: bool,
    ) -> Result<()> {
        require!(amount > 0, SospesoError::InvalidAmount);

        let sponsor = ctx.accounts.sponsor.key();
        let program = ctx.accounts.target_program.key();

        // Fund the escrow from the sponsor via a System transfer (the sponsor is
        // a plain wallet here, so a System transfer is valid).
        let cpi = anchor_lang::system_program::Transfer {
            from: ctx.accounts.sponsor.to_account_info(),
            to: ctx.accounts.sospeso.to_account_info(),
        };
        anchor_lang::system_program::transfer(
            CpiContext::new(ctx.accounts.system_program.to_account_info(), cpi),
            amount,
        )?;

        let pool = &mut ctx.accounts.sospeso;
        pool.sponsor = sponsor;
        pool.program = program;
        pool.lamports_total = amount;
        pool.lamports_remaining = amount;
        pool.max_per_claim = max_per_claim;
        pool.claims_count = 0;
        pool.max_claims = max_claims;
        pool.expiry_ts = expiry_ts;
        pool.nonce = nonce;
        pool.new_wallet_only = new_wallet_only;
        pool.bump = ctx.bumps.sospeso;
        pool._padding = [0u8; 6];

        emit!(SospesoCreated {
            sospeso: pool.key(),
            sponsor,
            amount,
        });
        Ok(())
    }

    /// Draw `amount` lamports for `beneficiary`, recording a ClaimReceipt.
    pub fn claim_sospeso(ctx: Context<ClaimSospeso>, amount: u64) -> Result<()> {
        require!(amount > 0, SospesoError::InvalidAmount);

        let now = Clock::get()?.unix_timestamp;
        let pool = &mut ctx.accounts.sospeso;

        require!(!pool.is_expired(now), SospesoError::Expired);
        require!(!pool.claims_exhausted(), SospesoError::ClaimCountExhausted);
        if pool.max_per_claim != 0 {
            require!(amount <= pool.max_per_claim, SospesoError::PerClaimCapExceeded);
        }
        require!(
            pool.lamports_remaining >= amount,
            SospesoError::InsufficientBudget
        );

        // Move lamports by direct balance mutation: the data PDA cannot be the
        // source of a System transfer. Guard the rent floor explicitly.
        let pool_ai = pool.to_account_info();
        let beneficiary_ai = ctx.accounts.beneficiary.to_account_info();
        let rent_floor = Rent::get()?.minimum_balance(pool_ai.data_len());

        let pool_balance = pool_ai.lamports();
        require!(
            pool_balance.saturating_sub(amount) >= rent_floor,
            SospesoError::BelowRentFloor
        );

        **pool_ai.try_borrow_mut_lamports()? = pool_balance
            .checked_sub(amount)
            .ok_or(SospesoError::Overflow)?;
        **beneficiary_ai.try_borrow_mut_lamports()? = beneficiary_ai
            .lamports()
            .checked_add(amount)
            .ok_or(SospesoError::Overflow)?;

        pool.lamports_remaining = pool
            .lamports_remaining
            .checked_sub(amount)
            .ok_or(SospesoError::Overflow)?;
