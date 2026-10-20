import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import type {
  IndicatorResponse,
  IndicatorsResponse,
  LineIndicatorsResponse,
  PortfolioIndicatorsResponse,
} from '../../api/generated';
import {
  IndicatorResponseStatusEnum,
  InstrumentSummaryResponseTypeEnum,
  LineIndicatorsResponseExclusionEnum,
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';
import { provideApiClient } from '../../core/api/api-client';
import { NOTIFY_SERVER_ERRORS } from '../../core/http/http-context';
import { Dashboard } from './dashboard';

const INDICATORS = '/api/v1/portfolio/indicators';
const NBSP = '\u00a0';
const NNBSP = '\u202f';

const ok = (value: string): IndicatorResponse => ({
  status: IndicatorResponseStatusEnum.Available,
  value,
});
const insufficient: IndicatorResponse = { status: IndicatorResponseStatusEnum.InsufficientHistory };
const notComputable: IndicatorResponse = { status: IndicatorResponseStatusEnum.NotComputable };

const lineIndicators: IndicatorsResponse = {
  asOf: '2026-10-16',
  closesOneYear: 253,
  performanceOneMonth: ok('2.35'),
  performanceThreeMonths: ok('5.10'),
  performanceSixMonths: ok('-1.20'),
  performanceOneYear: insufficient,
  volatility: ok('18.40'),
  maxDrawdown: ok('-12.30'),
  msciWorld: { outperformance: ok('3.10'), correlation: ok('0.85'), beta: ok('1.05') },
  cac40: { outperformance: ok('-0.50'), correlation: notComputable, beta: ok('0.00') },
  averageDailyTradedAmount: ok('1234567.89'),
  currency: 'EUR',
};

function lineOf(
  id: string,
  name: string,
  extra: Partial<LineIndicatorsResponse>,
): LineIndicatorsResponse {
  return {
    lineId: `l-${id}`,
    instrument: {
      id,
      isin: 'FR0000120073',
      mic: 'XPAR',
      ticker: name.slice(0, 3).toUpperCase(),
      name,
      type: InstrumentSummaryResponseTypeEnum.Stock,
      currency: 'EUR',
      tracked: true,
      peaEligibility: {
        status: PeaEligibilityResponseStatusEnum.Eligible,
        source: PeaEligibilityResponseSourceEnum.Rule,
      },
    },
    ...extra,
  };
}

const full: PortfolioIndicatorsResponse = {
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
    lineOf('a', 'Air Liquide', { weightPercent: '57.72', indicators: lineIndicators }),
    lineOf('c', 'Amundi MSCI World', { weightPercent: '33.33', indicators: lineIndicators }),
    lineOf('t', 'TotalEnergies', {
      indicators: { ...lineIndicators, closesOneYear: 12 },
      exclusion: LineIndicatorsResponseExclusionEnum.InsufficientHistory,
    }),
  ],
  curve: [
    { date: '2025-10-21', portfolio: '100.00', msciWorld: '100.00', cac40: '100.00' },
    { date: '2025-10-22', msciWorld: '101.20', cac40: '99.10' },
    { date: '2026-10-16', portfolio: '104.35', msciWorld: '102.10', cac40: '98.75' },
  ],
  correlations: {
    instrumentIds: ['a', 'c'],
    rows: [
      [ok('1.00'), ok('0.42')],
      [ok('0.42'), ok('1.00')],
    ],
  },
};

describe('Dashboard', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Dashboard>;
  let element: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function render(indicators: PortfolioIndicatorsResponse): Promise<void> {
    fixture = TestBed.createComponent(Dashboard);
    element = fixture.nativeElement as HTMLElement;
    const load = http.expectOne(INDICATORS);
    expect(load.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
    load.flush(indicators);
    await fixture.whenStable();
  }

  /** Text of the element an ARIA attribute points to. */
  function referenced(target: Element | null, attribute: string): string | undefined {
    const id = target?.getAttribute(attribute) ?? 'none';
    return element.querySelector(`#${id}`)?.textContent;
  }

  function line(
    id: string,
    name: string,
    extra: Partial<LineIndicatorsResponse>,
  ): LineIndicatorsResponse {
    return lineOf(id, name, extra);
  }

  function text(selector: string): string {
    return (element.querySelector(selector)?.textContent ?? '').replace(/[ \n]+/g, ' ').trim();
  }

  it('shows the total, the date of the prices and the risk cards as sent by the backend', async () => {
    await render(full);

    expect(text('[data-testid="card-total"]')).toBe(`3${NNBSP}000,00${NBSP}€`);
    expect(element.textContent).toContain('Cours au 16/10/2026');
    expect(text('[data-testid="card-volatility"]')).toBe(`16,20${NBSP}%`);
    expect(text('[data-testid="card-max-drawdown"]')).toBe(`-9,80${NBSP}%`);
    expect(text('[data-testid="card-herfindahl"]')).toBe('0,5359');
    expect(text('[data-testid="card-largest-line"]')).toBe(`57,72${NBSP}%`);
    expect(text('[data-testid="card-cash"]')).toBe(`8,95${NBSP}%`);
    expect(text('[data-testid="card-line-count"]')).toBe('3');
  });

  it('lists the indicators of each line and says which ones are left out', async () => {
    await render(full);

    const rows = [...element.querySelectorAll('[data-testid="line-indicators"] tbody tr')].map(
      (row) => row.textContent.replace(/[ \n]+/g, ' ').trim(),
    );
    expect(rows[0]).toContain(`57,72${NBSP}%`);
    expect(rows[0]).toContain(`+2,35${NBSP}%`);
    expect(rows[0]).toContain('Historique insuffisant');
    expect(rows[0]).toContain('1,05');
    expect(rows[0]).toMatch(/Oui$/);
    expect(rows[2]).toMatch(/Non : historique insuffisant$/);
    expect(text('[data-testid="dashboard-warnings"]')).toContain(
      'Exclue(s) de la volatilité, de la pire baisse, de la courbe et des corrélations : TotalEnergies (historique insuffisant).',
    );
    expect(text('[data-testid="dashboard-warnings"]')).toContain('Cours possiblement non ajustés');
  });

  it('draws an accessible curve with a gap and gives its values as a table', async () => {
    await render(full);

    const chart = element.querySelector('[data-testid="performance-chart"]');
    expect(chart?.getAttribute('role')).toBe('img');
    expect(chart?.getAttribute('aria-label')).toBe('Courbe base 100 sur un an');
    expect(text('figcaption')).toContain('comme s’il avait été détenu depuis un an');
    expect(referenced(chart, 'aria-describedby')).toBe(
      'Base 100 le 21/10/2025. Dernières valeurs au 16/10/2026 : Portefeuille actuel 104,35 ; ETF MSCI World (CW8) 102,10 ; ETF CAC 40 (CAC) 98,75.',
    );
    const paths = [...element.querySelectorAll('[data-testid="performance-chart"] path')].map(
      (path) => path.getAttribute('d'),
    );
    expect(paths).toHaveLength(3);
    expect(paths[0]?.match(/M/g)).toHaveLength(2);
    const cells = [...element.querySelectorAll('details tbody tr')].map((row) =>
      [...row.children].map((cell) => cell.textContent.trim()),
    );
    expect(cells[1]).toEqual(['22/10/2025', '—', '101,20', '99,10']);
  });

  it('keeps a benchmark without values in the legend, never draws it', async () => {
    await render({
      ...full,
      curve: full.curve.map(({ date, portfolio, msciWorld }) => ({ date, portfolio, msciWorld })),
    });

    expect(element.querySelectorAll('[data-testid="performance-chart"] path')).toHaveLength(2);
    expect(text('.chart__legend')).toContain('ETF CAC 40 (CAC) : non disponible');
  });

  it('shows a line without any close with the words of the portfolio page', async () => {
    await render({
      ...full,
      lines: [
        line('u', 'Sans Cours', { exclusion: LineIndicatorsResponseExclusionEnum.NotValued }),
        full.lines[0] ?? line('a', 'Air Liquide', {}),
      ],
    });

    const row = text('[data-testid="line-indicators"] tbody tr');
    expect(row).toContain('Cours indisponible');
    expect(row).not.toContain('Historique insuffisant');
    expect(row).toMatch(/Non : cours indisponible$/);
    expect(text('[data-testid="dashboard-warnings"]')).toContain('Sans Cours (cours indisponible)');
  });

  it('lets the keyboard reach every scrolling table', async () => {
    await render(full);

    const regions = [...element.querySelectorAll('[role="region"]')];
    expect(regions.map((region) => region.getAttribute('aria-label'))).toEqual([
      'Données du graphique',
      'Indicateurs des lignes',
      'Corrélations entre les lignes',
    ]);
    expect(regions.every((region) => region.getAttribute('tabindex') === '0')).toBe(true);
  });

  it('shows a progress bar while loading', () => {
    fixture = TestBed.createComponent(Dashboard);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();

    expect(element.querySelector('mat-progress-bar')).not.toBeNull();
    http.expectOne(INDICATORS).flush(full);
  });

  it('shows the correlations between the kept lines', async () => {
    await render(full);

    const cells = [
      ...element.querySelectorAll('[data-testid="correlations"] tbody tr:first-child > *'),
    ].map((cell) => cell.textContent.trim());
    expect(cells).toEqual(['Air Liquide', '1,00', '0,42']);
  });

  it('says why the curve and the correlations are missing', async () => {
    await render({
      ...full,
      asOf: undefined,
      volatility: insufficient,
      curve: [],
      correlations: { instrumentIds: [], rows: [] },
    });

    expect(element.querySelector('[data-testid="curve-unavailable"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="correlations-empty"]')).not.toBeNull();
    expect(element.textContent).toContain('Aucun cours connu');
    expect(text('[data-testid="card-volatility"]')).toBe('Historique insuffisant');
    expect(text('[data-testid="dashboard-warnings"]')).toContain('Aucun cours enregistré');
    expect(text('[data-testid="dashboard-warnings"]')).toContain('moins de 80 % des séances');
  });

  it('invites to declare lines when the portfolio is empty', async () => {
    await render({ ...full, lines: [] });

    expect(text('[data-testid="dashboard-empty"]')).toContain('Aucune ligne déclarée');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/portefeuille');
    expect(element.querySelector('[data-testid="performance-chart"]')).toBeNull();
  });

  it('shows a failure with its reference, then loads again on demand', async () => {
    fixture = TestBed.createComponent(Dashboard);
    element = fixture.nativeElement as HTMLElement;
    http
      .expectOne(INDICATORS)
      .flush(
        { status: 500, title: 'Error', code: 'internal.error', requestId: 'r9' },
        { status: 500, statusText: 'Server Error' },
      );
    await fixture.whenStable();

    expect(text('[role="alert"] p')).toBe(
      'Les indicateurs n’ont pas pu être chargés. Référence : r9',
    );
    element.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    http.expectOne(INDICATORS).flush(full);
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });
});
