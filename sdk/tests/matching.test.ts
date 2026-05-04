import {
  canCover,
  isEligible,
  selectBestPool,
  type MatchCriteria,
} from "../src/matching.js";
import { lamportsToSol, solToLamports, type Sospeso } from "../src/types.js";

function pool(overrides: Partial<Sospeso> = {}): Sospeso {
  return {
    id: "spo_test",
    label: "test pool",
    sponsor: "SponsorAAA",
    programId: "",
    pda: null,
    lamportsTotal: 1_000_000,
    lamportsRemaining: 1_000_000,
    maxPerClaim: 50_000,
    maxClaims: 10,
    claimsCount: 0,
    expiryTs: 0,
    newWalletOnly: false,
    ...overrides,
  };
}

const NOW = 1_000;

describe("canCover", () => {
  it("accepts a request within budget and caps", () => {
    expect(canCover(pool(), 40_000, NOW)).toBe(true);
  });

  it("rejects over the per-claim cap", () => {
    expect(canCover(pool({ maxPerClaim: 30_000 }), 40_000, NOW)).toBe(false);
  });

  it("rejects an expired pool", () => {
    expect(canCover(pool({ expiryTs: 500 }), 40_000, NOW)).toBe(false);
  });

  it("rejects when claim count exhausted", () => {
    expect(canCover(pool({ maxClaims: 2, claimsCount: 2 }), 40_000, NOW)).toBe(
      false,
    );
  });
});

describe("isEligible", () => {
  const criteria: MatchCriteria = {
    beneficiary: "NewWallet1",
    neededLamports: 40_000,
    isNewWallet: false,
  };

  it("blocks new-wallet-only pools for an active wallet", () => {
    expect(isEligible(pool({ newWalletOnly: true }), criteria, NOW)).toBe(false);
  });

  it("blocks the sponsor claiming its own pool", () => {
    expect(
      isEligible(pool({ sponsor: "NewWallet1" }), criteria, NOW),
    ).toBe(false);
  });

  it("respects a program restriction", () => {
    const restricted = pool({ programId: "ProgABC" });
    expect(isEligible(restricted, { ...criteria, programId: "ProgXYZ" }, NOW)).toBe(
      false,
    );
    expect(isEligible(restricted, { ...criteria, programId: "ProgABC" }, NOW)).toBe(
      true,
    );
  });
});

describe("selectBestPool", () => {
  it("prefers the smallest covering pool", () => {
    const big = pool({ id: "big", lamportsRemaining: 900_000 });
    const small = pool({ id: "small", lamportsRemaining: 200_000 });
    const chosen = selectBestPool([big, small], {
      beneficiary: "W",
      neededLamports: 40_000,
      isNewWallet: true,
    }, NOW);
    expect(chosen?.id).toBe("small");
  });

  it("returns null when nothing fits", () => {
    const tiny = pool({ lamportsRemaining: 100 });
    expect(
      selectBestPool([tiny], {
        beneficiary: "W",
        neededLamports: 40_000,
        isNewWallet: true,
      }, NOW),
    ).toBeNull();
  });

  it("breaks ties on nearest expiry", () => {
    const later = pool({ id: "later", lamportsRemaining: 300_000, expiryTs: 9_000 });
    const sooner = pool({ id: "sooner", lamportsRemaining: 300_000, expiryTs: 2_000 });
    const chosen = selectBestPool([later, sooner], {
      beneficiary: "W",
      neededLamports: 40_000,
      isNewWallet: true,
    }, NOW);
    expect(chosen?.id).toBe("sooner");
  });
});

describe("unit conversion", () => {
  it("round-trips lamports and SOL", () => {
    expect(lamportsToSol(1_000_000_000)).toBe(1);
    expect(solToLamports(0.002)).toBe(2_000_000);
  });
});
