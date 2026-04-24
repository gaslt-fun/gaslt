/**
 * Client-side sospeso matching, mirroring `gaslt_core::matching`.
 *
 * The selection policy prefers the pool with the smallest remaining budget that
 * still covers the request -- spend the change jar before the big fund -- and
 * breaks ties toward the nearest expiry.
 */

import type { Address, Lamports, Sospeso } from "./types.js";

/** What the caller is matching for. */
export interface MatchCriteria {
  beneficiary: Address;
  neededLamports: Lamports;
  /** Whether the beneficiary passes the new-wallet heuristic. */
  isNewWallet: boolean;
  /** Restrict to a program id (optional). */
  programId?: string;
}

/** Static budget check: can this pool cover the request right now? */
export function canCover(
  pool: Sospeso,
  neededLamports: Lamports,
  nowUnix: number,
): boolean {
  if (neededLamports <= 0) return false;
  if (pool.expiryTs !== 0 && pool.expiryTs <= nowUnix) return false;
  if (pool.maxClaims !== 0 && pool.claimsCount >= pool.maxClaims) return false;
  if (pool.lamportsRemaining < neededLamports) return false;
  if (pool.maxPerClaim !== 0 && neededLamports > pool.maxPerClaim) return false;
  return true;
}

/** Full eligibility: budget plus new-wallet and program gating. */
export function isEligible(
  pool: Sospeso,
  criteria: MatchCriteria,
  nowUnix: number,
): boolean {
  if (!canCover(pool, criteria.neededLamports, nowUnix)) return false;
  if (pool.sponsor === criteria.beneficiary) return false;
  if (
    criteria.programId &&
    pool.programId !== "" &&
    pool.programId !== criteria.programId
  ) {
    return false;
  }
  if (pool.newWalletOnly && !criteria.isNewWallet) return false;
  return true;
}

/** Pick the best eligible pool from a list, or null when nothing fits. */
export function selectBestPool(
  pools: Sospeso[],
  criteria: MatchCriteria,
  nowUnix: number,
): Sospeso | null {
  const expiryKey = (p: Sospeso) =>
    p.expiryTs === 0 ? Number.MAX_SAFE_INTEGER : p.expiryTs;

  let best: Sospeso | null = null;
  for (const pool of pools) {
    if (!isEligible(pool, criteria, nowUnix)) continue;
    if (best === null) {
      best = pool;
      continue;
    }
    if (pool.lamportsRemaining !== best.lamportsRemaining) {
      if (pool.lamportsRemaining < best.lamportsRemaining) best = pool;
    } else if (expiryKey(pool) < expiryKey(best)) {
      best = pool;
    }
  }
  return best;
}
