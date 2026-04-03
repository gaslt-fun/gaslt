//! Program error codes. These mirror `gaslt_core::ProtocolError` so the relayer
//! can pre-check off-chain and predict the exact on-chain rejection.

use anchor_lang::prelude::*;

#[error_code]
pub enum SospesoError {
    #[msg("requested amount is zero or invalid")]
    InvalidAmount,
    #[msg("pool does not have enough remaining lamports")]
    InsufficientBudget,
    #[msg("debit would push the escrow below its rent-exempt floor")]
    BelowRentFloor,
    #[msg("requested amount exceeds the per-claim cap")]
    PerClaimCapExceeded,
    #[msg("the pool has served its maximum number of claims")]
    ClaimCountExhausted,
    #[msg("the sospeso has expired")]
    Expired,
    #[msg("the sospeso has not yet expired")]
    NotExpired,
    #[msg("the signer is not the pool sponsor")]
    NotSponsor,
    #[msg("arithmetic overflow")]
    Overflow,
}
