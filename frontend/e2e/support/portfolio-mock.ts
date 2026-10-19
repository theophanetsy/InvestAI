import type { Page, Route } from '@playwright/test';
import { hasValidCsrfToken } from './api-mock';

// Same-origin paths (resolved against baseURL), like api-mock.ts.
export const PORTFOLIO_API = {
  profile: '/api/v1/profile',
  riskProfiles: '/api/v1/profile/risk-profiles',
  portfolio: '/api/v1/portfolio',
  lines: '/api/v1/portfolio/lines',
  line: '/api/v1/portfolio/lines/*',
  search: '/api/v1/market/instruments?query=*',
} as const;

const airLiquide = {
  id: '7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c',
  isin: 'FR0000120073',
  mic: 'XPAR',
  ticker: 'AI',
  name: 'Air Liquide',
  type: 'STOCK',
  currency: 'EUR',
  tracked: false,
  peaEligibility: { status: 'ELIGIBLE', source: 'RULE' },
};

const riskProfiles = [
  { code: 'PRUDENT', label: 'Prudent', description: 'Baisse annuelle supportable : 10 %.' },
  { code: 'BALANCED', label: 'Équilibré', description: 'Baisse annuelle supportable : 20 %.' },
  { code: 'DYNAMIC', label: 'Dynamique', description: 'Baisse annuelle supportable : 30 %.' },
  { code: 'AGGRESSIVE', label: 'Offensif', description: 'Baisse annuelle supportable : 40 %.' },
];

/** What the fake backend received, for the assertions of the test. */
export interface PortfolioRequests {
  savedProfile?: unknown;
  addedLine?: unknown;
  deletedLine?: string;
}

/**
 * Fakes the profile and portfolio API (contract: frontend/CLAUDE.md): no profile at first, an
 * empty portfolio, a search that finds Air Liquide, and a valuation computed by the "backend"
 * once the line is added (the front end never computes amounts).
 */
export async function mockPortfolioApi(page: Page): Promise<PortfolioRequests> {
  const requests: PortfolioRequests = {};
  let profile: object | null = null;
  let lines: object[] = [];

  await page.route(PORTFOLIO_API.riskProfiles, (route) => route.fulfill({ json: riskProfiles }));
  await page.route(PORTFOLIO_API.profile, async (route) => {
    if (route.request().method() === 'GET') {
      return profile
        ? route.fulfill({ json: profile })
        : route.fulfill({ status: 404, json: problem(404, 'profile.not-found') });
    }
    if (!(await hasValidCsrfToken(route))) {
      return csrfRefused(route);
    }
    requests.savedProfile = route.request().postDataJSON();
    profile = { ...(requests.savedProfile as object), currency: 'EUR', updatedAt: NOW };
    return route.fulfill({ json: profile });
  });
  await page.route(PORTFOLIO_API.search, (route) =>
    route.fulfill({ json: /air/i.test(route.request().url()) ? [airLiquide] : [] }),
  );
  await page.route(PORTFOLIO_API.lines, async (route) => {
    if (!(await hasValidCsrfToken(route))) {
      return csrfRefused(route);
    }
    requests.addedLine = route.request().postDataJSON();
    lines = [valuedLine];
    return route.fulfill({
      status: 201,
      json: { id: 'l1', instrumentId: airLiquide.id, quantity: 10, updatedAt: NOW },
    });
  });
  await page.route(PORTFOLIO_API.line, async (route) => {
    if (!(await hasValidCsrfToken(route))) {
      return csrfRefused(route);
    }
    requests.deletedLine = route.request().url().split('/').pop();
    lines = [];
    return route.fulfill({ status: 204 });
  });
  await page.route(PORTFOLIO_API.portfolio, (route) =>
    route.fulfill({
      json: {
        lines,
        total: lines.length === 0 ? '0.00' : '1731.60',
        currency: 'EUR',
        complete: true,
        unpricedLines: 0,
      },
    }),
  );
  return requests;
}

const NOW = '2026-10-19T08:00:00Z';

const valuedLine = {
  id: 'l1',
  instrument: airLiquide,
  quantity: 10,
  averagePrice: '150.1234',
  updatedAt: NOW,
  lastClose: { date: '2026-10-16', close: '173.1600' },
  value: '1731.60',
  unrealizedGain: '230.37',
  unrealizedGainPercent: '15.35',
  weightPercent: '100.00',
};

function problem(status: number, code: string): object {
  return { status, title: 'Error', code, requestId: 'e2e-req' };
}

function csrfRefused(route: Route): Promise<void> {
  return route.fulfill({ status: 403, json: problem(403, 'auth.csrf-invalid') });
}
