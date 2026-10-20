import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { provideApiClient } from './core/api/api-client';
import { withApiInterceptors } from './core/http/api-interceptors';

const UNAVAILABLE = { status: 'NOT_COMPUTABLE' };
const EMPTY_INDICATORS = {
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

describe('App routes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withApiInterceptors()),
        provideHttpClientTesting(),
        provideApiClient(),
        provideRouter(routes, withComponentInputBinding()),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  /** Navigates as a logged-in user (or not), answering the session and status calls. */
  async function open(url: string, loggedIn: boolean): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(url);
    // The guard asks for the session once the navigation has started.
    const me = await vi.waitFor(() => http.expectOne('/api/v1/auth/me'));
    if (loggedIn) {
      me.flush({ username: 'owner' });
    } else {
      me.flush(null, { status: 401, statusText: 'Unauthorized' });
    }
    await navigation;
    await harness.fixture.whenStable();
    for (const status of http.match('/api/v1/status')) {
      status.flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
    }
    // The dashboard loads the indicators of the declared portfolio.
    for (const indicators of http.match('/api/v1/portfolio/indicators')) {
      indicators.flush(EMPTY_INDICATORS);
    }
    await harness.fixture.whenStable();
    return harness.routeNativeElement ?? (harness.fixture.nativeElement as HTMLElement);
  }

  function heading(element: HTMLElement): string | undefined {
    return element.querySelector('h1')?.textContent.trim();
  }

  it('sends an anonymous user to the login screen, remembering the requested page', async () => {
    const element = await open('/simulation', false);

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fsimulation');
    expect(heading(element)).toBe('InvestAI');
    expect(element.querySelector('form')).not.toBeNull();
    expect(TestBed.inject(Title).getTitle()).toBe('Connexion · InvestAI');
  });

  it('opens the dashboard by default', async () => {
    const element = await open('/', true);

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(heading(element)).toBe('Tableau de bord');
    expect(TestBed.inject(Title).getTitle()).toBe('Tableau de bord · InvestAI');
    expect(element.querySelector('[data-testid="dashboard-empty"]')).not.toBeNull();
  });

  it('opens the profile page, which loads the profile', async () => {
    const profile = await open('/profil', true);
    http.expectOne('/api/v1/profile/risk-profiles').flush([]);
    http
      .expectOne('/api/v1/profile')
      .flush(
        { status: 404, title: 'Not Found', code: 'profile.not-found', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    await vi.waitFor(() => {
      expect(profile.textContent).toContain('Aucun profil enregistré');
    });
    expect(heading(profile)).toBe('Mon profil');
    expect(TestBed.inject(Title).getTitle()).toBe('Mon profil · InvestAI');
  });

  it('opens the portfolio page, which loads the valuation', async () => {
    const portfolio = await open('/portefeuille', true);
    http
      .expectOne('/api/v1/portfolio')
      .flush({ lines: [], total: '0.00', currency: 'EUR', complete: true, unpricedLines: 0 });
    expect(heading(portfolio)).toBe('Mon portefeuille');
    expect(TestBed.inject(Title).getTitle()).toBe('Mon portefeuille · InvestAI');
  });

  it('sends a logged-in user away from the login screen', async () => {
    await open('/login', true);

    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });

  it.each([
    ['/dashboard', 'Tableau de bord'],
    ['/assistant', 'Assistant'],
    ['/actu', 'Actu'],
    ['/simulation', 'Simulation'],
    ['/nope', 'Page introuvable'],
  ])('shows %s inside the application shell', async (url, title) => {
    const element = await open(url, true);

    expect(heading(element)).toBe(title);
    expect(element.closest('inv-shell')).not.toBeNull();
    expect(TestBed.inject(Title).getTitle()).toBe(`${title} · InvestAI`);
  });

  it('marks the current page in the navigation', async () => {
    const element = await open('/actu', true);

    const current = element.closest('inv-shell')?.querySelector('a[aria-current="page"]');
    expect(current?.textContent.trim()).toBe('Actu');
  });
});
