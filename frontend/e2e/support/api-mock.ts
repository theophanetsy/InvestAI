import type { Page, Route } from '@playwright/test';

// Paths are resolved against baseURL: only same-origin requests match (never http://localhost/…).
export const API = {
  status: '/api/v1/status',
  me: '/api/v1/auth/me',
  login: '/api/v1/auth/login',
  logout: '/api/v1/auth/logout',
  indicators: '/api/v1/portfolio/indicators',
} as const;

const UNAVAILABLE = { status: 'NOT_COMPUTABLE' };

/** Indicators of an empty declared portfolio: what the dashboard loads by default. */
export const EMPTY_INDICATORS = {
  total: '0.00',
  currency: 'EUR',
  composition: {
    lineCount: 0,
    largestLinePercent: UNAVAILABLE,
    herfindahlIndex: UNAVAILABLE,
    cashPercent: UNAVAILABLE,
    etfPercent: UNAVAILABLE,
    stockPercent: UNAVAILABLE,
    otherPercent: UNAVAILABLE,
  },
  volatility: UNAVAILABLE,
  maxDrawdown: UNAVAILABLE,
  lines: [],
  curve: [],
  correlations: { instrumentIds: [], rows: [] },
};

export const PASSWORD = 'correct-horse';

interface MockOptions {
  loggedIn: boolean;
  statusCode?: number;
  logoutStatus?: number;
}

function problem(status: number, code: string): object {
  return { status, title: 'Error', code, requestId: 'e2e-req' };
}

/**
 * Fakes the backend (ADR-0006/0007 contracts): a session that login and logout change, and the
 * CSRF double submit of Spring Security — every GET writes the `XSRF-TOKEN` cookie, a POST must
 * echo it in `X-XSRF-TOKEN`, login rotates it and logout deletes it.
 */
export async function mockApi(page: Page, options: MockOptions): Promise<void> {
  let loggedIn = options.loggedIn;
  let issued = 0;
  const newToken = (): Record<string, string> => {
    issued += 1;
    return { 'set-cookie': `XSRF-TOKEN=token-${String(issued)}; Path=/; SameSite=Strict` };
  };

  await page.route(API.status, (route) =>
    options.statusCode
      ? route.fulfill({ status: options.statusCode, headers: newToken() })
      : route.fulfill({
          headers: newToken(),
          json: { application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' },
        }),
  );
  await page.route(API.me, (route) =>
    loggedIn
      ? route.fulfill({ headers: newToken(), json: { username: 'owner' } })
      : route.fulfill({ status: 401, headers: newToken(), json: problem(401, 'auth.required') }),
  );
  // The dashboard (home page) loads the indicators; a test can route it again to change them.
  await page.route(API.indicators, (route) => route.fulfill({ json: EMPTY_INDICATORS }));
  await page.route(API.login, async (route) => {
    if (!(await hasValidCsrfToken(route))) {
      return route.fulfill({ status: 403, json: problem(403, 'auth.csrf-invalid') });
    }
    const body = route.request().postDataJSON() as { password?: string };
    if (body.password !== PASSWORD) {
      return route.fulfill({ status: 401, json: problem(401, 'auth.invalid-credentials') });
    }
    loggedIn = true;
    return route.fulfill({ status: 204, headers: newToken() });
  });
  await page.route(API.logout, async (route) => {
    if (!(await hasValidCsrfToken(route))) {
      return route.fulfill({ status: 403, json: problem(403, 'auth.csrf-invalid') });
    }
    const status = options.logoutStatus ?? 204;
    if (status !== 204) {
      return route.fulfill({ status, json: problem(status, 'internal.error') });
    }
    loggedIn = false;
    return route.fulfill({
      status,
      headers: { 'set-cookie': 'XSRF-TOKEN=; Path=/; Max-Age=0; SameSite=Strict' },
    });
  });
}

/** Spring Security double submit: the `X-XSRF-TOKEN` header must echo the `XSRF-TOKEN` cookie. */
export async function hasValidCsrfToken(route: Route): Promise<boolean> {
  const headers = await route.request().allHeaders();
  const cookie = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(headers['cookie'] ?? '')?.[1];
  return cookie !== undefined && headers['x-xsrf-token'] === cookie;
}
