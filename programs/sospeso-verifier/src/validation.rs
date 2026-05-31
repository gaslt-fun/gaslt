//! Pure input-validation helpers shared by the v0.1.2 instructions.
//!
//! Keeping these out of the instruction bodies makes the handlers read as a
//! checklist and keeps the validation rules in one auditable place. Every
//! function returns the canonical [`SospesoError`] for its failure so clients
//! get a stable, descriptive error code.

use anchor_lang::prelude::*;

use crate::errors::SospesoError;
use crate::state::{MAX_CATEGORY, MAX_DESCRIPTION_LEN, MAX_LABEL_LEN, MAX_URI_LEN};

/// A label must be non-empty and fit within [`MAX_LABEL_LEN`] bytes.
pub fn validate_label(label: &str) -> Result<()> {
    require!(!label.trim().is_empty(), SospesoError::LabelEmpty);
    require!(label.len() <= MAX_LABEL_LEN, SospesoError::LabelTooLong);
    Ok(())
}

/// A description may be empty but must fit within [`MAX_DESCRIPTION_LEN`] bytes.
pub fn validate_description(description: &str) -> Result<()> {
    require!(
        description.len() <= MAX_DESCRIPTION_LEN,
        SospesoError::DescriptionTooLong
    );
    Ok(())
}

/// A URI may be empty but must fit within [`MAX_URI_LEN`] bytes.
pub fn validate_uri(uri: &str) -> Result<()> {
    require!(uri.len() <= MAX_URI_LEN, SospesoError::UriTooLong);
    Ok(())
}

/// A category tag must be within `0..=MAX_CATEGORY`.
pub fn validate_category(category: u8) -> Result<()> {
    require!(category <= MAX_CATEGORY, SospesoError::InvalidCategory);
    Ok(())
}

/// Validate a full metadata payload in one call.
pub fn validate_meta(label: &str, description: &str, uri: &str, category: u8) -> Result<()> {
    validate_label(label)?;
    validate_description(description)?;
    validate_uri(uri)?;
    validate_category(category)?;
    Ok(())
}

/// A proposed new expiry must lie strictly in the future relative to `now`.
pub fn validate_future_ts(new_ts: i64, now: i64) -> Result<()> {
    require!(new_ts > now, SospesoError::ExpiryNotInFuture);
    Ok(())
}

/// A proposed new expiry must extend (be strictly later than) the current one.
pub fn validate_extension(new_ts: i64, current_ts: i64) -> Result<()> {
    require!(new_ts > current_ts, SospesoError::ExpiryNotExtended);
    Ok(())
}
