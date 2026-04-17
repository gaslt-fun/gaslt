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
