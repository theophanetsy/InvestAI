import { HttpClient, HttpContext, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../api/generated';
import { provideApiClient } from '../api/api-client';
import { AuthSession } from '../auth/auth-session';
import { withApiInterceptors } from './api-interceptors';
import { NOTIFY_SERVER_ERRORS, REDIRECT_ON_UNAUTHORIZED } from './http-context';
import { UNREACHABLE_MESSAGE } from './problem';

const URL = '/api/v1/things';

describe('API interceptors', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let router: Router;
  let navigate: ReturnType<typeof vi.spyOn>;
  const snackBar = { open: vi.fn() };

  beforeEach(() => {
    snackBar.open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withApiInterceptors()),
        provideHttpClientTesting(),
        provideApiClient(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    router = TestBed.inject(Router);
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
  });

  function problem(status: number, code: string): object {
    return { status, title: 'Error', code, requestId: 'req-42' };
  }

  function call(context?: HttpContext): Promise<unknown> {
    return firstValueFrom(client.post(URL, {}, { context }));
  }

  describe('on 401', () => {
    it('forgets the user and opens the login screen, back to the current page', async () => {
      const clear = vi.spyOn(TestBed.inject(AuthSession), 'clear');
      vi.spyOn(router, 'url', 'get').mockReturnValue('/simulation');

      const result = call();
      http.expectOne(URL).flush(problem(401, 'auth.required'), { status: 401, statusText: '' });

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(clear).toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/simulation' },
      });
    });

    it('does not nest the return address when already on the login screen', async () => {
      const clear = vi.spyOn(TestBed.inject(AuthSession), 'clear');
      vi.spyOn(router, 'url', 'get').mockReturnValue('/login?returnUrl=%2Fsimulation');

      const result = call();
      http.expectOne(URL).flush(problem(401, 'auth.required'), { status: 401, statusText: '' });

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(clear).toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('lets calls that handle their own 401 alone', async () => {
      const result = call(new HttpContext().set(REDIRECT_ON_UNAUTHORIZED, false));
      http.expectOne(URL).flush(problem(401, 'auth.required'), { status: 401, statusText: '' });

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('on 403 auth.csrf-invalid', () => {
    afterEach(() => {
      document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    });

    it('renews the token with a GET, then sends the request once more with the new token', async () => {
      document.cookie = 'XSRF-TOKEN=stale';
      const result = call();
      const rejected = http.expectOne(URL);
      expect(rejected.request.headers.get('X-XSRF-TOKEN')).toBe('stale');
      rejected.flush(problem(403, 'auth.csrf-invalid'), { status: 403, statusText: 'Forbidden' });
      const renewal = http.expectOne('/api/v1/status');
      expect(renewal.request.method).toBe('GET');
      // The backend answers the GET with a new cookie.
      document.cookie = 'XSRF-TOKEN=fresh';
      renewal.flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
      const retried = http.expectOne(URL);
      expect(retried.request.headers.get('X-XSRF-TOKEN')).toBe('fresh');
      retried.flush({ ok: true });

      expect(await result).toEqual({ ok: true });
    });

    it('sends the token that was missing from the first attempt, e.g. after a logout', async () => {
      const result = call();
      const rejected = http.expectOne(URL);
      expect(rejected.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      rejected.flush(problem(403, 'auth.csrf-invalid'), { status: 403, statusText: 'Forbidden' });
      document.cookie = 'XSRF-TOKEN=fresh';
      http
        .expectOne('/api/v1/status')
        .flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
      const retried = http.expectOne(URL);

      expect(retried.request.headers.get('X-XSRF-TOKEN')).toBe('fresh');
      retried.flush({ ok: true });
      await result;
    });

    it('gives up after one retry', async () => {
      const result = call();
      http
        .expectOne(URL)
        .flush(problem(403, 'auth.csrf-invalid'), { status: 403, statusText: 'Forbidden' });
      http
        .expectOne('/api/v1/status')
        .flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
      http
        .expectOne(URL)
        .flush(problem(403, 'auth.csrf-invalid'), { status: 403, statusText: 'Forbidden' });

      await expect(result).rejects.toMatchObject({ status: 403 });
    });

    it('does not retry another 403', async () => {
      const result = call();
      http
        .expectOne(URL)
        .flush(problem(403, 'auth.forbidden'), { status: 403, statusText: 'Forbidden' });

      await expect(result).rejects.toMatchObject({ status: 403 });
    });
  });

  describe('on server errors', () => {
    it('notifies a 5xx with the request id', async () => {
      const result = call();
      http.expectOne(URL).flush(problem(500, 'internal.error'), { status: 500, statusText: '' });

      await expect(result).rejects.toMatchObject({ status: 500 });
      expect(snackBar.open).toHaveBeenCalledWith(
        'Erreur du serveur, réessayez plus tard. Référence : req-42',
        'Fermer',
      );
    });

    it('notifies an unreachable server', async () => {
      const result = call();
      http.expectOne(URL).error(new ProgressEvent('error'));

      await expect(result).rejects.toMatchObject({ status: 0 });
      expect(snackBar.open).toHaveBeenCalledWith(UNREACHABLE_MESSAGE, 'Fermer');
    });

    it('leaves 4xx errors to the caller', async () => {
      const result = call();
      http
        .expectOne(URL)
        .flush(problem(400, 'validation.failed'), { status: 400, statusText: 'Bad Request' });

      await expect(result).rejects.toMatchObject({ status: 400 });
      expect(snackBar.open).not.toHaveBeenCalled();
    });

    it('stays quiet for calls that display their own errors', async () => {
      const result = call(new HttpContext().set(NOTIFY_SERVER_ERRORS, false));
      http.expectOne(URL).flush(problem(503, 'http.503'), { status: 503, statusText: '' });

      await expect(result).rejects.toMatchObject({ status: 503 });
      expect(snackBar.open).not.toHaveBeenCalled();
    });
  });

  it('applies to the generated client', async () => {
    const me = firstValueFrom(TestBed.inject(AuthService).me());
    http.expectOne('/api/v1/auth/me').flush(problem(401, 'auth.required'), {
      status: 401,
      statusText: '',
    });

    await expect(me).rejects.toMatchObject({ status: 401 });
    expect(navigate).toHaveBeenCalled();
  });
});
