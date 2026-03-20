//! End-to-end flow: open pools, match a beneficiary, claim, and read stats.

use gaslt_core::prelude::*;

fn pk(seed: u8) -> Pubkey {
    Pubkey::from_bytes([seed; 32])
}

#[test]
fn sponsor_funds_pool_and_new_user_claims() {
    let mut registry = Registry::new();

    // A sponsor opens a new-wallet-only onboarding pool.
    let onboarding = registry.insert(Sospeso::open(
        SospesoParams::new(pk(1), 2_000_000)
            .with_max_per_claim(50_000)
            .with_max_claims(40)
            .new_wallets_only(),
        0,
    ));

    // A larger community pool open to everyone.
    let _community = registry.insert(Sospeso::open(
        SospesoParams::new(pk(2), 5_000_000).with_max_per_claim(80_000),
        1,
    ));

    // A brand-new wallet asks for sponsorship.
    let newbie = pk(42);
    let matcher = Matcher::default();
    let outcome = matcher
        .find(
            &registry,
            &MatchCriteria::new(newbie, 40_000),
            &WalletProfile::verified(0),
            10,
        )
        .expect("a new wallet matches the onboarding pool");

    // The smaller, new-wallet-only pool should win.
    assert_eq!(outcome.id, onboarding);
    assert!(outcome.new_wallet_only);

    // The claim succeeds and debits the pool.
    let receipt = registry
        .claim(&outcome.id, ClaimRequest::new(newbie, 40_000), 11)
        .unwrap();
    assert_eq!(receipt.amount, 40_000);
    assert_eq!(
        registry.get(&outcome.id).unwrap().lamports_remaining,
        2_000_000 - 40_000
    );

    let stats = registry.stats();
    assert_eq!(stats.total_sospesos, 2);
    assert_eq!(stats.total_claims, 1);
    assert_eq!(stats.total_lamports_drawn, 40_000);
}

#[test]
fn exhausted_pool_falls_through_to_next() {
    let mut registry = Registry::new();
    let tiny = registry.insert(Sospeso::open(
        SospesoParams::new(pk(1), 60_000)
            .with_max_per_claim(50_000)
            .with_max_claims(1),
        0,
    ));
    let backup = registry.insert(Sospeso::open(
        SospesoParams::new(pk(2), 500_000).with_max_per_claim(50_000),
        1,
    ));

    let matcher = Matcher::default();

    // First wallet drains the tiny pool's single claim.
    let first = matcher
        .find(
            &registry,
            &MatchCriteria::new(pk(10), 40_000),
            &WalletProfile::verified(0),
            5,
        )
        .unwrap();
    assert_eq!(first.id, tiny);
    registry
        .claim(&first.id, ClaimRequest::new(pk(10), 40_000), 5)
        .unwrap();

    // Second wallet can no longer use the tiny pool (claim count exhausted),
    // so it falls through to the backup.
    let second = matcher
        .find(
            &registry,
            &MatchCriteria::new(pk(11), 40_000),
            &WalletProfile::verified(0),
            6,
        )
        .unwrap();
    assert_eq!(second.id, backup);
}
