/**
 * Shared types mirroring the on-chain `sospeso_verifier` account layout and the
 * relayer service's JSON shapes.
 */

/** Lamports are represented as a JS number; callers needing >2^53 use bigint. */
export type Lamports = number;

/** A base58 account address. */
export type Address = string;

/** A sponsored gas pool. */
export interface Sospeso {
  /** Registry id (distinct from the on-chain PDA). */
  id: string;
  /** Human label shown in UIs. */
  label: string;
  /** Sponsor wallet (base58). */
  sponsor: Address;
  /** Program id the pool targets (empty = any). */
  programId: string;
  /** On-chain PDA address, when known. */
  pda: string | null;
  /** Total lamports committed. */
  lamportsTotal: Lamports;
  /** Lamports still available. */
  lamportsRemaining: Lamports;
  /** Max lamports per single claim (0 = no cap). */
  maxPerClaim: Lamports;
  /** Max claims allowed (0 = unbounded). */
  maxClaims: number;
  /** Claims served so far. */
  claimsCount: number;
  /** Unix seconds after which the pool expires (0 = never). */
  expiryTs: number;
  /** Whether only new wallets may claim. */
  newWalletOnly: boolean;
}

/** Request body for the relayer's sponsorship endpoint. */
export interface SponsorRequest {
  /** base64 of a partially-signed transaction. */
  transactionBase64: string;
  /** Beneficiary wallet (base58). */
  beneficiary: Address;
  /** Optional pool the sponsorship should draw from. */
  sospesoId?: string;
}

/** Relayer response on success. */
export interface SponsorResult {
  ok: true;
  dryRun: boolean;
  signature: string | null;
  sponsor: Address;
  estimatedFeeLamports: Lamports;
}

/** Aggregate protocol stats. */
export interface Stats {
  totalSospesos: number;
  totalSponsored: number;
  totalLamportsSaved: Lamports;
}

/** Number of lamports in one SOL. */
export const LAMPORTS_PER_SOL = 1_000_000_000;

/** Convert lamports to a SOL number. */
export function lamportsToSol(lamports: Lamports): number {
  return lamports / LAMPORTS_PER_SOL;
}

/** Convert a SOL amount to integer lamports. */
export function solToLamports(sol: number): Lamports {
  return Math.round(sol * LAMPORTS_PER_SOL);
}
