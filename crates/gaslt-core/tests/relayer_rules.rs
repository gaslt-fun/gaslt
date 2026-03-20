//! Rules a relayer applies before fronting a fee: rate limiting and escrow
//! solvency, exercised together the way the service wires them.

use gaslt_core::prelude::*;

fn pk(seed: u8) -> Pubkey {
    Pubkey::from_bytes([seed; 32])
}

#[test]
fn rate_limit_then_escrow_debit() {
    let mut limiter = RateLimiter::new();
    let per_wallet = RateRule::new("wallet", 3, 86_400_000);

    let wallet = pk(7);
    let key = format!("wallet:{}", wallet.short());

    let mut escrow = Escrow::with_rent_floor(1_000_000, 100_000);

    // Three sponsored claims are allowed within the daily window.
    for _ in 0..3 {
        limiter.enforce(&key, &per_wallet, 0).unwrap();
        escrow.debit(50_000).unwrap();
    }
    assert_eq!(escrow.balance(), 1_000_000 - 150_000);

    // The fourth request in the same window is throttled before touching funds.
    let blocked = limiter.enforce(&key, &per_wallet, 1_000).unwrap_err();
    assert!(blocked.is_transient());
    assert_eq!(escrow.balance(), 1_000_000 - 150_000);
}

#[test]
fn escrow_refuses_to_breach_rent_floor() {
    let mut escrow = Escrow::with_rent_floor(200_000, 150_000);
    assert!(escrow.debit(50_000).is_ok());
    // Only 0 claimable now; any further debit must fail.
    let err = escrow.debit(1).unwrap_err();
    assert_eq!(err.code(), "below_rent_floor");
}

#[test]
fn windows_are_independent_per_key() {
    let mut limiter = RateLimiter::new();
    let rule = RateRule::new("ip", 2, 60_000);

    limiter.enforce("a", &rule, 0).unwrap();
    limiter.enforce("a", &rule, 0).unwrap();
    assert!(limiter.enforce("a", &rule, 0).is_err());

    // A different key has its own budget.
    assert!(limiter.enforce("b", &rule, 0).is_ok());
}
