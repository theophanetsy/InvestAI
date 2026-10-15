import type { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse, HttpXsrfTokenExtractor } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { SystemService } from '../../api/generated';
import { selfHandledErrors } from './http-context';
import { toProblem } from './problem';

/** Header in which Angular sends the `XSRF-TOKEN` cookie (ADR-0007). */
const XSRF_HEADER = 'X-XSRF-TOKEN';

/**
 * A 403 `auth.csrf-invalid` means the `XSRF-TOKEN` cookie was missing or stale (ADR-0007), e.g.
 * right after a logout: any GET makes the backend write a new one, then the request is sent once
 * more with that new token. Angular's XSRF interceptor runs before this one and never overwrites
 * the header, so the retry sets it itself. The retry goes to the next handler only, so it never
 * loops through this interceptor.
 */
export const csrfRetryInterceptor: HttpInterceptorFn = (request, next) => {
  const system = inject(SystemService);
  const tokens = inject(HttpXsrfTokenExtractor);
  return next(request).pipe(
    catchError((error: unknown) =>
      isCsrfRejection(error)
        ? system.getStatus('body', false, { context: selfHandledErrors() }).pipe(
            switchMap(() => {
              const token = tokens.getToken();
              const headers =
                token === null
                  ? request.headers.delete(XSRF_HEADER)
                  : request.headers.set(XSRF_HEADER, token);
              return next(request.clone({ headers }));
            }),
          )
        : throwError(() => error),
    ),
  );
};

function isCsrfRejection(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === 403 &&
    toProblem(error).code === 'auth.csrf-invalid'
  );
}
