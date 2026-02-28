//! Fixed-window rate limiting along the ip / wallet / sospeso axes.
//!
//! The relayer throttles sponsorship requests so a single actor cannot drain a
//! pool. This is a self-contained, deterministic implementation: the caller
//! injects the current time in milliseconds, so windows are reproducible in
//! tests and identical whether the counters live in memory or in Redis.

use std::collections::HashMap;

use crate::error::{ProtocolError, Result};

/// A single rate rule: at most `limit` events per `window_ms` milliseconds.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RateRule {
    /// Human/log label for the axis this rule guards.
    pub axis: &'static str,
    /// Maximum number of events allowed inside one window.
    pub limit: u32,
    /// Window length in milliseconds.
    pub window_ms: u64,
}

impl RateRule {
    /// Build a rate rule.
    pub const fn new(axis: &'static str, limit: u32, window_ms: u64) -> Self {
        RateRule {
            axis,
            limit,
            window_ms,
        }
    }

    /// The fixed-window bucket index a timestamp falls into.
    pub fn bucket(&self, now_ms: u64) -> u64 {
        if self.window_ms == 0 {
            return 0;
        }
        now_ms / self.window_ms
    }
}

/// The outcome of a rate check.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RateDecision {
    /// Whether the event is permitted.
    pub allowed: bool,
    /// The count after this event (or the count that tripped the limit).
    pub count: u32,
    /// The axis label of the rule evaluated.
    pub axis: &'static str,
}

#[derive(Clone, Copy, Debug)]
struct Counter {
    bucket: u64,
    count: u32,
}

/// In-memory fixed-window counter store keyed by an arbitrary string.
#[derive(Debug, Default)]
pub struct RateLimiter {
    counters: HashMap<String, Counter>,
}

impl RateLimiter {
    /// Create an empty limiter.
    pub fn new() -> Self {
        RateLimiter {
            counters: HashMap::new(),
        }
    }

    /// Number of distinct keys currently tracked.
    pub fn tracked_keys(&self) -> usize {
        self.counters.len()
    }

    /// Peek at the current count for `key` within `rule`'s window without
    /// incrementing it.
    pub fn peek(&self, key: &str, rule: &RateRule, now_ms: u64) -> u32 {
        match self.counters.get(key) {
            Some(c) if c.bucket == rule.bucket(now_ms) => c.count,
            _ => 0,
        }
    }

    /// Increment the counter for `key` and decide whether the event is allowed.
    ///
    /// The counter is only advanced while within bounds, so a rejected event
    /// does not consume budget beyond the one that tripped the limit.
    pub fn check(&mut self, key: &str, rule: &RateRule, now_ms: u64) -> RateDecision {
        let bucket = rule.bucket(now_ms);
        let entry = self.counters.entry(key.to_string()).or_insert(Counter {
            bucket,
            count: 0,
        });
        if entry.bucket != bucket {
            entry.bucket = bucket;
            entry.count = 0;
        }
