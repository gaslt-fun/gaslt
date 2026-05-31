//! sospeso_verifier -- Gaslt on-chain escrow + claim accounting for sponsored gas.
//!
//! A "sospeso" (Italian: "suspended") is a pre-paid pool of lamports a sponsor
//! deposits so that future beneficiaries (typically brand-new wallets) can have
//! their transaction gas reimbursed. The program is the trust anchor: it holds
//! the escrowed SOL in a PDA, enforces per-claim caps, expiry, a configurable
//! new-wallet-only flag, and prevents double claims via a per-beneficiary
//! ClaimReceipt PDA whose mere existence blocks a second claim.
//!
//! Pure SOL (lamports) accounting -- no SPL / Token-2022.

use anchor_lang::prelude::*;

pub mod errors;
pub mod events;
pub mod state;
pub mod validation;

use errors::SospesoError;
use events::{
    BridgeRegistered, BridgeRevoked, BridgeUpdated, ExpiryExtended, MetaCleared, MetaSet,
    MetaUpdated, PoolRegistered, PoolStatsSynced, RegistryInitialized, VersionEmitted,
};
use state::{
    BarRegistry, Bridge, ClaimReceipt, RegistryEntry, Sospeso, SospesoMeta, BRIDGE_ENDPOINT_LEN,
    BRIDGE_KIND_LEN, BRIDGE_LABEL_LEN,
};
use validation::{validate_extension, validate_future_ts, validate_meta};

declare_id!("44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW");

/// On-chain program version. Bumped on each upgrade so the deployed binary
/// (and IDL) provably changes; also lets clients read the live version.
#[constant]
pub const VERSION: &str = "0.1.3";

#[program]
pub mod sospeso_verifier {
    use super::*;

    /// Sponsor opens a sospeso pool and funds it with `amount` lamports.
    ///
    /// The PDA is created rent-exempt; `amount` is transferred on top of rent
    /// via a System Program CPI (sponsor signs). `lamports_total` /
    /// `lamports_remaining` track only the claimable balance (rent stays in the
    /// account forever and is never handed out to beneficiaries).
    pub fn create_sospeso(
        ctx: Context<CreateSospeso>,
        nonce: u64,
        amount: u64,
        max_per_claim: u64,
        max_claims: u32,
        expiry_ts: i64,
        new_wallet_only: bool,
    ) -> Result<()> {
        require!(amount > 0, SospesoError::ZeroAmount);
        require!(max_claims > 0, SospesoError::ZeroMaxClaims);
        require!(max_per_claim > 0, SospesoError::ZeroMaxPerClaim);
        require!(max_per_claim <= amount, SospesoError::AmountTooLarge);

        let now = Clock::get()?.unix_timestamp;
        require!(expiry_ts > now, SospesoError::Expired);

        // Fund the escrow: sponsor -> Sospeso PDA (on top of rent already paid by init).
        anchor_lang::system_program::transfer(
            CpiContext::new(
                ctx.accounts.system_program.to_account_info(),
                anchor_lang::system_program::Transfer {
                    from: ctx.accounts.sponsor.to_account_info(),
                    to: ctx.accounts.sospeso.to_account_info(),
                },
            ),
            amount,
        )?;

        let sospeso = &mut ctx.accounts.sospeso;
        sospeso.sponsor = ctx.accounts.sponsor.key();
        sospeso.lamports_total = amount;
        sospeso.lamports_remaining = amount;
        sospeso.max_per_claim = max_per_claim;
        sospeso.claims_count = 0;
        sospeso.max_claims = max_claims;
        sospeso.expiry_ts = expiry_ts;
        sospeso.new_wallet_only = new_wallet_only;
        sospeso.nonce = nonce;
        sospeso.bump = ctx.bumps.sospeso;

        emit!(SospesoCreated {
            sospeso: sospeso.key(),
            sponsor: sospeso.sponsor,
            amount,
            max_per_claim,
            max_claims,
            expiry_ts,
            new_wallet_only,
            nonce,
        });

        Ok(())
    }

    /// A beneficiary (or a relayer paying rent on their behalf) claims `amount`
    /// lamports from the pool. The ClaimReceipt PDA is `init`-ed here, so a
    /// second claim by the same beneficiary fails at account creation -- the
    /// receipt's existence is the double-claim guard.
    ///
    /// Because the Sospeso PDA carries data, lamports cannot leave it through a
    /// System transfer; we move them by directly mutating both accounts'
    /// lamports (the escrow stays above its rent floor since only the
    /// claimable `lamports_remaining` is ever debited).
    pub fn claim_sospeso(ctx: Context<ClaimSospeso>, amount: u64) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;

        {
            let sospeso = &ctx.accounts.sospeso;
            require!(now < sospeso.expiry_ts, SospesoError::Expired);
            require!(
                sospeso.claims_count < sospeso.max_claims,
                SospesoError::ClaimCapReached
            );
            require!(amount > 0, SospesoError::ZeroAmount);
            require!(
                amount <= sospeso.max_per_claim,
                SospesoError::AmountTooLarge
            );
            require!(
                amount <= sospeso.lamports_remaining,
                SospesoError::InsufficientFunds
            );
        }

        // Move lamports: Sospeso PDA -> beneficiary. Direct lamport math because
        // the source PDA holds data (System transfer would fail).
        let sospeso_ai = ctx.accounts.sospeso.to_account_info();
        let beneficiary_ai = ctx.accounts.beneficiary.to_account_info();
        {
            let mut from = sospeso_ai.try_borrow_mut_lamports()?;
            let mut to = beneficiary_ai.try_borrow_mut_lamports()?;
            **from = from
                .checked_sub(amount)
                .ok_or(SospesoError::InsufficientFunds)?;
            **to = to.checked_add(amount).ok_or(SospesoError::Overflow)?;
        }

        let sospeso = &mut ctx.accounts.sospeso;
        sospeso.lamports_remaining = sospeso
            .lamports_remaining
            .checked_sub(amount)
            .ok_or(SospesoError::InsufficientFunds)?;
        sospeso.claims_count = sospeso
            .claims_count
            .checked_add(1)
            .ok_or(SospesoError::Overflow)?;

        let receipt = &mut ctx.accounts.claim_receipt;
        receipt.beneficiary = ctx.accounts.beneficiary.key();
        receipt.amount = amount;
        receipt.ts = now;
        receipt.bump = ctx.bumps.claim_receipt;

        emit!(SospesoClaimed {
            sospeso: sospeso.key(),
            beneficiary: receipt.beneficiary,
            amount,
            claims_count: sospeso.claims_count,
            lamports_remaining: sospeso.lamports_remaining,
            ts: now,
        });

        Ok(())
    }

    /// Sponsor adds more lamports to an existing, non-expired pool.
    pub fn top_up(ctx: Context<TopUp>, amount: u64) -> Result<()> {
        require!(amount > 0, SospesoError::ZeroAmount);

        let now = Clock::get()?.unix_timestamp;
        require!(now < ctx.accounts.sospeso.expiry_ts, SospesoError::Expired);

        anchor_lang::system_program::transfer(
            CpiContext::new(
                ctx.accounts.system_program.to_account_info(),
                anchor_lang::system_program::Transfer {
                    from: ctx.accounts.sponsor.to_account_info(),
                    to: ctx.accounts.sospeso.to_account_info(),
                },
            ),
            amount,
        )?;

        let sospeso = &mut ctx.accounts.sospeso;
        sospeso.lamports_total = sospeso
            .lamports_total
            .checked_add(amount)
            .ok_or(SospesoError::Overflow)?;
        sospeso.lamports_remaining = sospeso
            .lamports_remaining
            .checked_add(amount)
            .ok_or(SospesoError::Overflow)?;

        emit!(SospesoToppedUp {
            sospeso: sospeso.key(),
            sponsor: sospeso.sponsor,
            amount,
            lamports_remaining: sospeso.lamports_remaining,
        });

        Ok(())
    }

    /// After expiry, the sponsor reclaims the entire remaining (claimable)
    /// balance. Rent stays in the PDA. Uses direct lamport math for the same
    /// data-bearing-PDA reason as `claim_sospeso`.
    pub fn reclaim(ctx: Context<Reclaim>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let amount = {
            let sospeso = &ctx.accounts.sospeso;
            require!(now >= sospeso.expiry_ts, SospesoError::NotExpiredYet);
            sospeso.lamports_remaining
        };

        if amount > 0 {
            let sospeso_ai = ctx.accounts.sospeso.to_account_info();
            let sponsor_ai = ctx.accounts.sponsor.to_account_info();
            let mut from = sospeso_ai.try_borrow_mut_lamports()?;
            let mut to = sponsor_ai.try_borrow_mut_lamports()?;
            **from = from
                .checked_sub(amount)
                .ok_or(SospesoError::InsufficientFunds)?;
            **to = to.checked_add(amount).ok_or(SospesoError::Overflow)?;
        }

        let sospeso = &mut ctx.accounts.sospeso;
        sospeso.lamports_remaining = 0;

        emit!(SospesoReclaimed {
            sospeso: sospeso.key(),
            sponsor: sospeso.sponsor,
            amount,
        });

        Ok(())
    }

    // =====================================================================
    // v0.1.2 instructions. All additive: none reads or writes the four
    // accounts/instructions above in a way that changes their behaviour.
    // =====================================================================

    /// Sponsor attaches descriptive metadata (label / description / URI /
    /// category) to one of their pools, stored in a dedicated `SospesoMeta`
    /// PDA so the pool account itself is never touched.
    pub fn set_meta(
        ctx: Context<SetMeta>,
        label: String,
        description: String,
        uri: String,
        category: u8,
    ) -> Result<()> {
        validate_meta(&label, &description, &uri, category)?;

        let now = Clock::get()?.unix_timestamp;
        let meta = &mut ctx.accounts.meta;
        meta.sospeso = ctx.accounts.sospeso.key();
        meta.sponsor = ctx.accounts.sponsor.key();
        meta.label = label;
        meta.description = description;
        meta.uri = uri;
        meta.category = category;
        meta.created_ts = now;
        meta.updated_ts = now;
        meta.bump = ctx.bumps.meta;

        emit!(MetaSet {
            sospeso: meta.sospeso,
            sponsor: meta.sponsor,
            category,
            ts: now,
        });

        Ok(())
    }

    /// Sponsor overwrites an existing pool's metadata. The account is fixed-size
    /// (strings sized at max) so no reallocation is needed.
    pub fn update_meta(
        ctx: Context<UpdateMeta>,
        label: String,
        description: String,
        uri: String,
        category: u8,
    ) -> Result<()> {
        validate_meta(&label, &description, &uri, category)?;

        let now = Clock::get()?.unix_timestamp;
        let meta = &mut ctx.accounts.meta;
        meta.label = label;
        meta.description = description;
        meta.uri = uri;
        meta.category = category;
        meta.updated_ts = now;

        emit!(MetaUpdated {
            sospeso: meta.sospeso,
            sponsor: meta.sponsor,
            category,
            ts: now,
        });

        Ok(())
    }

    /// Sponsor closes a pool's metadata account, refunding its rent.
    pub fn clear_meta(ctx: Context<ClearMeta>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        emit!(MetaCleared {
            sospeso: ctx.accounts.meta.sospeso,
            sponsor: ctx.accounts.meta.sponsor,
            ts: now,
        });
        Ok(())
    }

    /// Sponsor pushes a pool's expiry further into the future. Only the
    /// `expiry_ts` field is touched -- the account layout is unchanged, so this
    /// is safe for pools created before 0.1.2. The new expiry must be both in
    /// the future and strictly later than the current expiry.
    pub fn extend_expiry(ctx: Context<ExtendExpiry>, new_expiry_ts: i64) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let old_expiry_ts = ctx.accounts.sospeso.expiry_ts;

        validate_future_ts(new_expiry_ts, now)?;
        validate_extension(new_expiry_ts, old_expiry_ts)?;

        let sospeso = &mut ctx.accounts.sospeso;
        sospeso.expiry_ts = new_expiry_ts;

        emit!(ExpiryExtended {
            sospeso: sospeso.key(),
            sponsor: sospeso.sponsor,
            old_expiry_ts,
            new_expiry_ts,
        });

        Ok(())
    }

    /// Initialize the singleton `BarRegistry` aggregate. Permissionless: whoever
    /// calls it becomes the recorded (informational) authority.
    pub fn init_registry(ctx: Context<InitRegistry>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let registry = &mut ctx.accounts.registry;
        registry.authority = ctx.accounts.payer.key();
        registry.total_pools = 0;
        registry.total_suspended_lamports = 0;
        registry.total_claims = 0;
        registry.created_ts = now;
        registry.last_update_ts = now;
        registry.bump = ctx.bumps.registry;

        emit!(RegistryInitialized {
            registry: registry.key(),
            authority: registry.authority,
            ts: now,
        });

        Ok(())
    }

    /// Fold a pool into the registry aggregate for the first time. The `init`-ed
    /// `RegistryEntry` PDA blocks a pool from being counted twice.
    pub fn register_pool(ctx: Context<RegisterPool>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let lamports = ctx.accounts.sospeso.lamports_total;
        let claims = ctx.accounts.sospeso.claims_count;

        let registry = &mut ctx.accounts.registry;
        registry.total_pools = registry
            .total_pools
            .checked_add(1)
            .ok_or(SospesoError::Overflow)?;
        registry.total_suspended_lamports = registry
            .total_suspended_lamports
            .checked_add(lamports as u128)
            .ok_or(SospesoError::Overflow)?;
        registry.total_claims = registry
            .total_claims
            .checked_add(claims as u64)
            .ok_or(SospesoError::Overflow)?;
        registry.last_update_ts = now;

        let entry = &mut ctx.accounts.entry;
        entry.sospeso = ctx.accounts.sospeso.key();
        entry.registry = registry.key();
        entry.recorded_lamports = lamports;
        entry.recorded_claims = claims;
        entry.registered_ts = now;
        entry.synced_ts = now;
        entry.bump = ctx.bumps.entry;

        emit!(PoolRegistered {
            registry: registry.key(),
            sospeso: entry.sospeso,
            recorded_lamports: lamports,
            recorded_claims: claims,
            total_pools: registry.total_pools,
        });

        Ok(())
    }

    /// Re-sync a registered pool: add the lamports/claims that accrued since the
    /// last record (from top-ups and claims) into the registry aggregate. Deltas
    /// are computed against the entry's recorded values, so re-running is safe
    /// and never double-counts.
    pub fn sync_pool_stats(ctx: Context<SyncPoolStats>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let lamports = ctx.accounts.sospeso.lamports_total;
        let claims = ctx.accounts.sospeso.claims_count;

        let lamports_delta = lamports.saturating_sub(ctx.accounts.entry.recorded_lamports);
        let claims_delta = claims.saturating_sub(ctx.accounts.entry.recorded_claims);

        let registry = &mut ctx.accounts.registry;
        registry.total_suspended_lamports = registry
            .total_suspended_lamports
            .checked_add(lamports_delta as u128)
            .ok_or(SospesoError::Overflow)?;
        registry.total_claims = registry
            .total_claims
            .checked_add(claims_delta as u64)
            .ok_or(SospesoError::Overflow)?;
        registry.last_update_ts = now;

        let entry = &mut ctx.accounts.entry;
        entry.recorded_lamports = lamports;
        entry.recorded_claims = claims;
        entry.synced_ts = now;

        emit!(PoolStatsSynced {
            registry: registry.key(),
            sospeso: entry.sospeso,
            lamports_delta,
            claims_delta,
            total_suspended_lamports: registry.total_suspended_lamports,
            total_claims: registry.total_claims,
        });

        Ok(())
    }

    /// Emit the live on-chain [`VERSION`] as an event so indexers and clients can
    /// confirm which build is deployed without an RPC account read.
    pub fn emit_version(ctx: Context<EmitVersion>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        emit!(VersionEmitted {
            version: VERSION.to_string(),
            caller: ctx.accounts.caller.key(),
            ts: now,
        });
        Ok(())
    }

    // =====================================================================
    // v0.1.3 instructions -- the off-chain-service bridge registry. Additive:
    // none of these read or write any pre-existing account, so all prior
    // instructions keep their exact behaviour and account layouts.
    // =====================================================================

    /// Register a bridge linking the program to an off-chain service. Creates
    /// the `[b"bridge", authority]` PDA, marks it `enabled`, and stamps both
    /// timestamps. All payload fields are fixed-size ascii byte arrays so the
    /// consuming service can decode them at constant offsets.
    pub fn register_bridge(
        ctx: Context<RegisterBridge>,
        kind: [u8; BRIDGE_KIND_LEN],
        endpoint: [u8; BRIDGE_ENDPOINT_LEN],
        label: [u8; BRIDGE_LABEL_LEN],
    ) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;

        let bridge = &mut ctx.accounts.bridge;
        bridge.authority = ctx.accounts.authority.key();
        bridge.kind = kind;
        bridge.endpoint = endpoint;
        bridge.label = label;
        bridge.enabled = true;
        bridge.registered_at = now;
        bridge.updated_at = now;
        bridge.bump = ctx.bumps.bridge;

        emit!(BridgeRegistered {
            bridge: bridge.key(),
            authority: bridge.authority,
            kind,
            enabled: true,
            ts: now,
        });

        Ok(())
    }

    /// Update a bridge's endpoint, label, and enabled flag. The account is
    /// fixed-size, so no reallocation is needed. Only the bridge `authority`
    /// (enforced by `has_one`) may call this.
    pub fn update_bridge(
        ctx: Context<UpdateBridge>,
        endpoint: [u8; BRIDGE_ENDPOINT_LEN],
        label: [u8; BRIDGE_LABEL_LEN],
        enabled: bool,
    ) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;

        let bridge = &mut ctx.accounts.bridge;
        bridge.endpoint = endpoint;
        bridge.label = label;
        bridge.enabled = enabled;
        bridge.updated_at = now;

        emit!(BridgeUpdated {
            bridge: bridge.key(),
            authority: bridge.authority,
            enabled,
            ts: now,
        });

        Ok(())
    }

    /// Revoke a bridge: close its account and refund the rent to the authority.
    /// Only the bridge `authority` (enforced by `has_one`) may call this.
    pub fn revoke_bridge(ctx: Context<RevokeBridge>) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        emit!(BridgeRevoked {
            bridge: ctx.accounts.bridge.key(),
            authority: ctx.accounts.bridge.authority,
            ts: now,
        });
        Ok(())
    }
}

// -------------------------------------------------------------------------
// Accounts contexts
// -------------------------------------------------------------------------

#[derive(Accounts)]
#[instruction(nonce: u64)]
pub struct CreateSospeso<'info> {
    #[account(mut)]
    pub sponsor: Signer<'info>,

    #[account(
        init,
        payer = sponsor,
        space = 8 + Sospeso::LEN,
        seeds = [b"sospeso", sponsor.key().as_ref(), &nonce.to_le_bytes()],
        bump,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ClaimSospeso<'info> {
    /// Pays for the ClaimReceipt rent. May be the beneficiary itself or a
    /// relayer fronting the rent (Octane pattern). Must sign.
    #[account(mut)]
    pub payer: Signer<'info>,

    /// The wallet credited with the claimed lamports and bound into the
    /// receipt seeds. Not required to sign (a relayer can claim on its behalf),
    /// but it is the identity the per-beneficiary double-claim guard keys on.
    /// CHECK: only used as a lamport destination and as a receipt seed; no data
    /// is read from or written to this account.
    #[account(mut)]
    pub beneficiary: UncheckedAccount<'info>,

    #[account(
        mut,
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        init,
        payer = payer,
        space = 8 + ClaimReceipt::LEN,
        seeds = [b"claim", sospeso.key().as_ref(), beneficiary.key().as_ref()],
        bump,
    )]
    pub claim_receipt: Box<Account<'info, ClaimReceipt>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct TopUp<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        mut,
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Reclaim<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        mut,
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,
}

// -------------------------------------------------------------------------
// v0.1.2 account contexts
// -------------------------------------------------------------------------

#[derive(Accounts)]
pub struct SetMeta<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        init,
        payer = sponsor,
        space = 8 + SospesoMeta::LEN,
        seeds = [b"meta", sospeso.key().as_ref()],
        bump,
    )]
    pub meta: Box<Account<'info, SospesoMeta>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct UpdateMeta<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        mut,
        seeds = [b"meta", sospeso.key().as_ref()],
        bump = meta.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
        constraint = meta.sospeso == sospeso.key() @ SospesoError::SospesoMismatch,
    )]
    pub meta: Box<Account<'info, SospesoMeta>>,
}

#[derive(Accounts)]
pub struct ClearMeta<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        mut,
        close = sponsor,
        seeds = [b"meta", sospeso.key().as_ref()],
        bump = meta.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
        constraint = meta.sospeso == sospeso.key() @ SospesoError::SospesoMismatch,
    )]
    pub meta: Box<Account<'info, SospesoMeta>>,
}

#[derive(Accounts)]
pub struct ExtendExpiry<'info> {
    #[account(mut, address = sospeso.sponsor @ SospesoError::Unauthorized)]
    pub sponsor: Signer<'info>,

    #[account(
        mut,
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
        has_one = sponsor @ SospesoError::Unauthorized,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,
}

#[derive(Accounts)]
pub struct InitRegistry<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,

    #[account(
        init,
        payer = payer,
        space = 8 + BarRegistry::LEN,
        seeds = [b"registry"],
        bump,
    )]
    pub registry: Box<Account<'info, BarRegistry>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct RegisterPool<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,

    #[account(
        mut,
        seeds = [b"registry"],
        bump = registry.bump,
    )]
    pub registry: Box<Account<'info, BarRegistry>>,

    #[account(
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        init,
        payer = payer,
        space = 8 + RegistryEntry::LEN,
        seeds = [b"regentry", sospeso.key().as_ref()],
        bump,
    )]
    pub entry: Box<Account<'info, RegistryEntry>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct SyncPoolStats<'info> {
    #[account(
        mut,
        seeds = [b"registry"],
        bump = registry.bump,
    )]
    pub registry: Box<Account<'info, BarRegistry>>,

    #[account(
        seeds = [b"sospeso", sospeso.sponsor.as_ref(), &sospeso.nonce.to_le_bytes()],
        bump = sospeso.bump,
    )]
    pub sospeso: Box<Account<'info, Sospeso>>,

    #[account(
        mut,
        seeds = [b"regentry", sospeso.key().as_ref()],
        bump = entry.bump,
        constraint = entry.sospeso == sospeso.key() @ SospesoError::SospesoMismatch,
        constraint = entry.registry == registry.key() @ SospesoError::RegistryMismatch,
    )]
    pub entry: Box<Account<'info, RegistryEntry>>,
}

#[derive(Accounts)]
pub struct EmitVersion<'info> {
    pub caller: Signer<'info>,
}

// -------------------------------------------------------------------------
// v0.1.3 account contexts -- bridge registry
// -------------------------------------------------------------------------

#[derive(Accounts)]
pub struct RegisterBridge<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,

    #[account(
        init,
        payer = authority,
        space = 8 + Bridge::LEN,
        seeds = [b"bridge", authority.key().as_ref()],
        bump,
    )]
    pub bridge: Box<Account<'info, Bridge>>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct UpdateBridge<'info> {
    #[account(mut, address = bridge.authority @ SospesoError::Unauthorized)]
    pub authority: Signer<'info>,

    #[account(
        mut,
        seeds = [b"bridge", bridge.authority.as_ref()],
        bump = bridge.bump,
        has_one = authority @ SospesoError::Unauthorized,
    )]
    pub bridge: Box<Account<'info, Bridge>>,
}

#[derive(Accounts)]
pub struct RevokeBridge<'info> {
    #[account(mut, address = bridge.authority @ SospesoError::Unauthorized)]
    pub authority: Signer<'info>,

    #[account(
        mut,
        close = authority,
        seeds = [b"bridge", bridge.authority.as_ref()],
        bump = bridge.bump,
        has_one = authority @ SospesoError::Unauthorized,
    )]
    pub bridge: Box<Account<'info, Bridge>>,
}

// -------------------------------------------------------------------------
// Events
// -------------------------------------------------------------------------

#[event]
pub struct SospesoCreated {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub amount: u64,
    pub max_per_claim: u64,
    pub max_claims: u32,
    pub expiry_ts: i64,
    pub new_wallet_only: bool,
    pub nonce: u64,
}

#[event]
pub struct SospesoClaimed {
    pub sospeso: Pubkey,
    pub beneficiary: Pubkey,
    pub amount: u64,
    pub claims_count: u32,
    pub lamports_remaining: u64,
    pub ts: i64,
}

#[event]
pub struct SospesoToppedUp {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub amount: u64,
    pub lamports_remaining: u64,
}

#[event]
pub struct SospesoReclaimed {
    pub sospeso: Pubkey,
    pub sponsor: Pubkey,
    pub amount: u64,
}
