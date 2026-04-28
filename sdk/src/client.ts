/**
 * Thin HTTP client for the sospeso relayer service. Uses the global `fetch`
 * (Node 18+ / browsers); no chain SDK dependency, so it stays tree-shakeable.
 */

import type {
  Sospeso,
  SponsorRequest,
  SponsorResult,
  Stats,
} from "./types.js";

/** Options for constructing a client. */
export interface ClientOptions {
  /** Base URL of the relayer service, e.g. https://gaslt.fun/api. */
  baseUrl: string;
  /** Optional fetch implementation (defaults to the global `fetch`). */
  fetchImpl?: typeof fetch;
}

/** Error thrown when the service returns a non-2xx response. */
export class GasltApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly reason: string,
  ) {
    super(`gaslt api error ${status}: ${reason}`);
    this.name = "GasltApiError";
  }
}

/** Client for the registry, matching, and relayer endpoints. */
export class GasltClient {
  private readonly baseUrl: string;
  private readonly doFetch: typeof fetch;

  constructor(options: ClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, "");
    const f = options.fetchImpl ?? globalThis.fetch;
    if (!f) {
      throw new Error("no fetch implementation available; pass fetchImpl");
    }
    this.doFetch = f;
  }

  private async get<T>(path: string): Promise<T> {
    const res = await this.doFetch(`${this.baseUrl}${path}`, {
      headers: { accept: "application/json" },
    });
    return this.parse<T>(res);
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    const res = await this.doFetch(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify(body),
    });
    return this.parse<T>(res);
  }

  private async parse<T>(res: Response): Promise<T> {
    const text = await res.text();
    const json = text ? JSON.parse(text) : {};
    if (!res.ok) {
      const reason =
        typeof json === "object" && json && "reason" in json
          ? String((json as { reason: unknown }).reason)
          : res.statusText;
      throw new GasltApiError(res.status, reason);
    }
    return json as T;
  }

  /** List available sospesos. */
  async listSospesos(): Promise<Sospeso[]> {
    const data = await this.get<{ sospesos: Sospeso[] }>("/sospeso");
    return data.sospesos;
  }

  /** Fetch aggregate stats. */
  async stats(): Promise<Stats> {
    return this.get<Stats>("/stats");
  }

  /** Submit a sponsorship request to the relayer. */
  async sponsor(request: SponsorRequest): Promise<SponsorResult> {
    return this.post<SponsorResult>("/sponsor", request);
  }
}
