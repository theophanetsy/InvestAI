import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiClient } from '../api/api-client';
import { NOTIFY_SERVER_ERRORS } from '../http/http-context';
import { BackendStatus } from './backend-status';

describe('BackendStatus', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(BackendStatus);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a loading state before the backend answers', async () => {
    const element = await render();

    expect(element.textContent.trim()).toBe('Connexion au backend…');
    http.expectOne('/api/v1/status');
  });

  it('shows the backend as connected when the status call succeeds', async () => {
    const fixture = TestBed.createComponent(BackendStatus);
    http
      .expectOne('/api/v1/status')
      .flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent.trim()).toBe('Backend connecté');
  });

  it('shows the backend as unavailable, without a notification, when the call fails', async () => {
    const fixture = TestBed.createComponent(BackendStatus);
    const request = http.expectOne('/api/v1/status');
    request.flush('boom', { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent.trim()).toBe('Backend indisponible');
    expect(request.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
  });
});
