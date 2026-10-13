import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function statusText(element: HTMLElement): string {
    return element.querySelector('[data-testid="backend-status"]')?.textContent.trim() ?? '';
  }

  it('renders the application title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toBe(
      'InvestAI',
    );
  });

  it('shows a loading state before the backend answers', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(statusText(fixture.nativeElement as HTMLElement)).toBe('Connexion au backend…');
  });

  it('shows the backend as connected when the status call succeeds', async () => {
    const fixture = TestBed.createComponent(App);
    http
      .expectOne('/api/v1/status')
      .flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
    await fixture.whenStable();

    expect(statusText(fixture.nativeElement as HTMLElement)).toBe('Backend connecté');
  });

  it('shows the backend as unavailable when the status call fails', async () => {
    const fixture = TestBed.createComponent(App);
    http
      .expectOne('/api/v1/status')
      .flush('boom', { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(statusText(fixture.nativeElement as HTMLElement)).toBe('Backend indisponible');
  });
});
