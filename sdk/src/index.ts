/**
 * @gaslt/sdk -- TypeScript client for the sospeso gas-abstraction protocol.
 *
 * ```ts
 * import { GasltClient, selectBestPool, lamportsToSol } from "@gaslt/sdk";
 *
 * const client = new GasltClient({ baseUrl: "https://gaslt.fun/api" });
 * const pools = await client.listSospesos();
 * const match = selectBestPool(pools, {
 *   beneficiary: wallet,
 *   neededLamports: 40_000,
 *   isNewWallet: true,
 * }, Math.floor(Date.now() / 1000));
 * ```
 */

export * from "./types.js";
export * from "./matching.js";
export * from "./client.js";

/** The protocol version this SDK targets. */
export const PROTOCOL_VERSION = "0.4.2";
