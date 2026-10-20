import type { Page } from '@playwright/test';
import { API } from './api-mock';

const ok = (value: string): object => ({ status: 'AVAILABLE', value });
const insufficient = { status: 'INSUFFICIENT_HISTORY' };

const instrument = (id: string, ticker: string, name: string, type: string): object => ({
  id,
  isin: 'FR0000120073',
  mic: 'XPAR',
  ticker,
  name,
  type,
  currency: 'EUR',
  tracked: true,
  peaEligibility: { status: 'ELIGIBLE', source: 'RULE' },
});

const lineIndicators = {
  asOf: '2026-10-16',
  closesOneYear: 253,
  performanceOneMonth: ok('2.35'),
  performanceThreeMonths: ok('5.10'),
  performanceSixMonths: ok('-1.20'),
  performanceOneYear: ok('8.40'),
  volatility: ok('18.40'),
  maxDrawdown: ok('-12.30'),
  msciWorld: { outperformance: ok('3.10'), correlation: ok('0.85'), beta: ok('1.05') },
  cac40: { outperformance: ok('-0.50'), correlation: ok('0.70'), beta: ok('0.95') },
  averageDailyTradedAmount: ok('1234567.89'),
  currency: 'EUR',
};

/** Synthetic base-100 curve of 253 sessions, with a missing close of the portfolio. */
function curve(): object[] {
  const start = Date.UTC(2025, 9, 21);
  return Array.from({ length: 253 }, (_, index) => ({
    date: new Date(start + index * 86_400_000).toISOString().slice(0, 10),
    portfolio: index === 120 ? undefined : (100 + index / 50).toFixed(2),
    msciWorld: (100 + index / 80).toFixed(2),
    cac40: (100 - index / 100).toFixed(2),
  }));
}

/** Indicators of a declared PEA of three lines, one left out for lack of history. */
export const PORTFOLIO_INDICATORS = {
  asOf: '2026-10-16',
  total: '3000.00',
  currency: 'EUR',
  composition: {
    lineCount: 3,
    largestLinePercent: ok('57.72'),
    herfindahlIndex: ok('0.5359'),
    cashPercent: ok('8.95'),
    etfPercent: ok('33.33'),
    stockPercent: ok('57.72'),
    otherPercent: ok('0.00'),
  },
  volatility: ok('16.20'),
  maxDrawdown: ok('-9.80'),
  lines: [
    {
      lineId: 'l1',
      instrument: instrument('a', 'AI', 'Air Liquide', 'STOCK'),
      weightPercent: '57.72',
      indicators: lineIndicators,
    },
    {
      lineId: 'l2',
      instrument: instrument('c', 'CW8', 'Amundi MSCI World', 'ETF'),
      weightPercent: '33.33',
      indicators: lineIndicators,
    },
    {
      lineId: 'l3',
      instrument: instrument('t', 'NEW', 'Nouvelle cotée', 'STOCK'),
      indicators: { ...lineIndicators, closesOneYear: 30, performanceOneYear: insufficient },
      exclusion: 'INSUFFICIENT_HISTORY',
    },
  ],
  curve: curve(),
  correlations: {
    instrumentIds: ['a', 'c'],
    rows: [
      [ok('1.00'), ok('0.42')],
      [ok('0.42'), ok('1.00')],
    ],
  },
};

/** Fakes the indicators of the declared portfolio (contract: frontend/CLAUDE.md). */
export async function mockDashboardApi(page: Page): Promise<void> {
  await page.route(API.indicators, (route) => route.fulfill({ json: PORTFOLIO_INDICATORS }));
}
