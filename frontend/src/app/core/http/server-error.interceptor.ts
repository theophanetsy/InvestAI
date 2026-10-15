import type { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { NOTIFY_SERVER_ERRORS } from './http-context';
import { UNREACHABLE_MESSAGE, toProblem, withReference } from './problem';

const SERVER_ERROR_MESSAGE = 'Erreur du serveur, réessayez plus tard.';

/**
 * Network failures and server errors (5xx) are not the screen's business: they are notified here,
 * with the request id for support. 4xx errors stay with the caller, which knows what to say.
 * The error is still passed on to the caller.
 */
export const serverErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const snackBar = inject(MatSnackBar);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        (error.status === 0 || error.status >= 500) &&
        request.context.get(NOTIFY_SERVER_ERRORS)
      ) {
        const problem = toProblem(error);
        const message =
          problem.status === 0 ? UNREACHABLE_MESSAGE : withReference(SERVER_ERROR_MESSAGE, problem);
        snackBar.open(message, 'Fermer');
      }
      return throwError(() => error);
    }),
  );
};
