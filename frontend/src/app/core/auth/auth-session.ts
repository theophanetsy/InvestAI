import { HttpErrorResponse } from '@angular/common/http';
import { Service, computed, inject, signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import type { CurrentUserResponse, LoginRequest } from '../../api/generated';
import { AuthService } from '../../api/generated';
import { selfHandledErrors } from '../http/http-context';

/**
 * Who is logged in (ADR-0007). The session itself is an HttpOnly cookie, invisible to the code:
 * this service only remembers the answer of `GET /api/v1/auth/me`, asked once.
 */
@Service()
export class AuthSession {
  private readonly api = inject(AuthService);

  /** `undefined` until the backend has answered, `null` when anonymous. */
  private readonly state = signal<CurrentUserResponse | null | undefined>(undefined);
  /** The pending `me` call, shared by concurrent checks (e.g. two guards). */
  private check: Observable<boolean> | undefined;

  /** The logged-in user, `null` when anonymous or not known yet. */
  readonly user = computed(() => this.state() ?? null);

  /**
   * Whether a user is logged in; asks the backend until it has answered. Only a 401 is remembered
   * as anonymous: when the backend cannot be reached, the next check asks again.
   */
  isAuthenticated(): Observable<boolean> {
    const state = this.state();
    if (state !== undefined) {
      return of(state !== null);
    }
    this.check ??= this.loadUser().pipe(
      map(() => true),
      catchError((error: unknown) => {
        if (isUnauthorized(error)) {
          this.state.set(null);
        }
        return of(false);
      }),
      finalize(() => {
        this.check = undefined;
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    return this.check;
  }

  /** Logs in, then loads the user; fails with the backend error (e.g. 401 invalid credentials). */
  login(credentials: LoginRequest): Observable<void> {
    return this.api.login(credentials, 'body', false, { context: selfHandledErrors() }).pipe(
      switchMap(() => this.loadUser()),
      map(() => undefined),
    );
  }

  /**
   * Logs out; a 401 means the session was already over, which is a success too. Any other failure
   * keeps the user and is passed on, for the caller to display.
   */
  logout(): Observable<void> {
    return this.api.logout('body', false, { context: selfHandledErrors() }).pipe(
      catchError((error: unknown) => (isUnauthorized(error) ? of(null) : throwError(() => error))),
      tap(() => {
        this.state.set(null);
      }),
      map(() => undefined),
    );
  }

  /** Forgets the user when the backend says the session is over (401 on another call). */
  clear(): void {
    this.state.set(null);
  }

  private loadUser(): Observable<CurrentUserResponse> {
    return this.api.me('body', false, { context: selfHandledErrors() }).pipe(
      tap((user) => {
        this.state.set(user);
      }),
    );
  }
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
