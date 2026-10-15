import type { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthSession } from '../auth/auth-session';
import { REDIRECT_ON_UNAUTHORIZED } from './http-context';

const LOGIN_URL = '/login';

/**
 * A 401 means the session is over (expired, or logged out elsewhere): forget the user and show the
 * login screen, which comes back to the current page (unless already there: concurrent 401s
 * must not nest the return address). Authentication calls opt out
 * ({@link REDIRECT_ON_UNAUTHORIZED}). The error is still passed on to the caller.
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSession);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        request.context.get(REDIRECT_ON_UNAUTHORIZED)
      ) {
        session.clear();
        if (!router.url.startsWith(LOGIN_URL)) {
          void router.navigate([LOGIN_URL], { queryParams: { returnUrl: router.url } });
        }
      }
      return throwError(() => error);
    }),
  );
};
