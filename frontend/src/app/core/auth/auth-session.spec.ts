import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideApiClient } from '../api/api-client';
import { NOTIFY_SERVER_ERRORS, REDIRECT_ON_UNAUTHORIZED } from '../http/http-context';
import { AuthSession } from './auth-session';

const ME = '/api/v1/auth/me';

describe('AuthSession', () => {
  let http: HttpTestingController;
  let session: AuthSession;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSession);
  });

  afterEach(() => {
    http.verify();
  });

  it('asks the backend once, then answers from memory', async () => {
    const first = firstValueFrom(session.isAuthenticated());
    const request = http.expectOne(ME);
    request.flush({ username: 'owner' });

    expect(await first).toBe(true);
    expect(await firstValueFrom(session.isAuthenticated())).toBe(true);
    expect(session.user()).toEqual({ username: 'owner' });
    expect(request.request.context.get(REDIRECT_ON_UNAUTHORIZED)).toBe(false);
    expect(request.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
  });

  it('is anonymous when the backend answers 401', async () => {
    const authenticated = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await authenticated).toBe(false);
    expect(session.user()).toBeNull();
    expect(await firstValueFrom(session.isAuthenticated())).toBe(false);
  });

  it('shares a pending check between concurrent callers', async () => {
    const first = firstValueFrom(session.isAuthenticated());
    const second = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });

    expect(await Promise.all([first, second])).toEqual([true, true]);
  });

  it('is not logged in, but asks again next time, when the backend cannot be reached', async () => {
    const authenticated = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).error(new ProgressEvent('error'));
    expect(await authenticated).toBe(false);

    const retry = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });
    expect(await retry).toBe(true);
  });

  it('logs in, then loads the user', async () => {
    const done = firstValueFrom(session.login({ username: 'owner', password: 'secret' }));

    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ username: 'owner', password: 'secret' });
    expect(login.request.context.get(REDIRECT_ON_UNAUTHORIZED)).toBe(false);
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(ME).flush({ username: 'owner' });

    await done;
    expect(session.user()).toEqual({ username: 'owner' });
  });

  it('fails the login without loading the user when the credentials are rejected', async () => {
    const done = firstValueFrom(session.login({ username: 'owner', password: 'wrong' }));
    http
      .expectOne('/api/v1/auth/login')
      .flush({ code: 'auth.invalid-credentials' }, { status: 401, statusText: 'Unauthorized' });

    await expect(done).rejects.toMatchObject({ status: 401 });
    expect(session.user()).toBeNull();
  });

  it('logs out and forgets the user', async () => {
    const check = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });
    await check;

    const done = firstValueFrom(session.logout());
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.method).toBe('POST');
    expect(logout.request.context.get(REDIRECT_ON_UNAUTHORIZED)).toBe(false);
    expect(logout.request.context.get(NOTIFY_SERVER_ERRORS)).toBe(false);
    logout.flush(null, { status: 204, statusText: 'No Content' });

    await done;
    expect(session.user()).toBeNull();
  });

  it('treats a logout without session (401) as done', async () => {
    const check = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });
    await check;

    const done = firstValueFrom(session.logout());
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 401, statusText: 'Unauthorized' });

    await done;
    expect(session.user()).toBeNull();
  });

  it('keeps the user and fails when the logout is refused', async () => {
    const check = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });
    await check;

    const done = firstValueFrom(session.logout());
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 403, statusText: 'Forbidden' });

    await expect(done).rejects.toMatchObject({ status: 403 });
    expect(session.user()).toEqual({ username: 'owner' });
  });

  it('forgets the user when the session is known to be over', async () => {
    const check = firstValueFrom(session.isAuthenticated());
    http.expectOne(ME).flush({ username: 'owner' });
    await check;

    session.clear();

    expect(session.user()).toBeNull();
    expect(await firstValueFrom(session.isAuthenticated())).toBe(false);
  });
});
