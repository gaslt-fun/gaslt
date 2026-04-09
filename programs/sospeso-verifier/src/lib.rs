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
        pool.claims_count = pool
            .claims_count
            .checked_add(1)
            .ok_or(SospesoError::Overflow)?;

        let receipt = &mut ctx.accounts.receipt;
        receipt.beneficiary = ctx.accounts.beneficiary.key();
        receipt.amount = amount;
        receipt.ts = now;
        receipt.bump = ctx.bumps.receipt;

        emit!(SospesoClaimed {
            sospeso: pool.key(),
            beneficiary: receipt.beneficiary,
            amount,
        });
        Ok(())
    }

    /// Add `amount` lamports to an existing pool (sponsor only).
    pub fn top_up(ctx: Context<TopUp>, amount: u64) -> Result<()> {
        require!(amount > 0, SospesoError::InvalidAmount);

        let cpi = anchor_lang::system_program::Transfer {
            from: ctx.accounts.sponsor.to_account_info(),
            to: ctx.accounts.sospeso.to_account_info(),
        };
        anchor_lang::system_program::transfer(
            CpiContext::new(ctx.accounts.system_program.to_account_info(), cpi),
            amount,
        )?;

        let pool = &mut ctx.accounts.sospeso;
        pool.lamports_total = pool
            .lamports_total
            .checked_add(amount)
            .ok_or(SospesoError::Overflow)?;
        pool.lamports_remaining = pool
            .lamports_remaining
            .checked_add(amount)
            .ok_or(SospesoError::Overflow)?;
        Ok(())
    }

    /// After expiry, sweep the remaining claimable lamports back to the sponsor
    /// and close the pool account.
    pub fn reclaim(ctx: Context<Reclaim>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let pool = &ctx.accounts.sospeso;
        require!(pool.is_expired(now), SospesoError::NotExpired);
        // The `close = sponsor` constraint returns all lamports (including rent)
        // to the sponsor; nothing else to do here.
        emit!(SospesoReclaimed {
            sospeso: pool.key(),
            sponsor: ctx.accounts.sponsor.key(),
        });
        Ok(())
    }
}

#[derive(Accounts)]
#[instruction(nonce: u64)]
pub struct CreateSospeso<'info> {
    #[account(mut)]
    pub sponsor: Signer<'info>,
    /// CHECK: only the key is read, to bind the pool to a target program.
    pub target_program: UncheckedAccount<'info>,
    #[account(
        init,
        payer = sponsor,
        space = 8 + Sospeso::INIT_SPACE,
        seeds = [Sospeso::SEED, sponsor.key().as_ref(), &nonce.to_le_bytes()],
        bump,
    )]
    pub sospeso: Account<'info, Sospeso>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ClaimSospeso<'info> {
    /// Pays for the receipt rent. May be the beneficiary or a relayer.
    #[account(mut)]
    pub payer: Signer<'info>,
    /// CHECK: credited with the claimed lamports; not required to sign so a
    /// relayer can claim on its behalf. Bound into the receipt seeds.
    #[account(mut)]
    pub beneficiary: UncheckedAccount<'info>,
    #[account(mut)]
    pub sospeso: Account<'info, Sospeso>,
    #[account(
        init,
        payer = payer,
        space = 8 + ClaimReceipt::INIT_SPACE,
        seeds = [ClaimReceipt::SEED, sospeso.key().as_ref(), beneficiary.key().as_ref()],
        bump,
    )]
    pub receipt: Account<'info, ClaimReceipt>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct TopUp<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::NotSponsor)]
    pub sponsor: Signer<'info>,
    #[account(mut)]
    pub sospeso: Account<'info, Sospeso>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Reclaim<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::NotSponsor)]
    pub sponsor: Signer<'info>,
    #[account(mut, close = sponsor)]
    pub sospeso: Account<'info, Sospeso>,
}

#[event]
pub struct SospesoCreated {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub amount: u64,
}

#[event]
pub struct SospesoClaimed {
    pub sospeso: Pubkey,
    pub beneficiary: Pubkey,
    pub amount: u64,
}

#[event]
pub struct SospesoReclaimed {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
}
