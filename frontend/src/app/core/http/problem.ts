import { HttpErrorResponse } from '@angular/common/http';
import type { Problem } from '../../api/generated';

/** Correlation header set by the backend on every response (ADR-0006). */
const REQUEST_ID_HEADER = 'X-Request-Id';

/**
 * The error of an API call as a {@link Problem}: the problem+json body when the backend sent one,
 * otherwise a problem built from the response (`network.unavailable` when the server could not be
 * reached, `http.<status>` for a body that is not a problem, e.g. from a proxy).
 */
export function toProblem(error: HttpErrorResponse): Problem {
  if (isProblem(error.error)) {
    return error.error;
  }
  const unreachable = error.status === 0;
  return {
    status: error.status,
    title: unreachable ? 'Network error' : `HTTP ${String(error.status)}`,
    code: unreachable ? 'network.unavailable' : `http.${String(error.status)}`,
    requestId: error.headers.get(REQUEST_ID_HEADER) ?? '',
  };
}

/** A user-facing message followed by the request id, which support uses to find the logs. */
export function withReference(message: string, problem: Problem): string {
  return problem.requestId ? `${message} Référence : ${problem.requestId}` : message;
}

export const UNREACHABLE_MESSAGE = 'Serveur injoignable. Vérifiez votre connexion et réessayez.';

/**
 * What to tell the user when an action failed: the server could not be reached, or the action
 * failed with the reference of the request.
 */
export function failureMessage(failure: string, error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return failure;
  }
  const problem = toProblem(error);
  return problem.status === 0 ? UNREACHABLE_MESSAGE : withReference(failure, problem);
}

function isProblem(body: unknown): body is Problem {
  return (
    typeof body === 'object' &&
    body !== null &&
    'status' in body &&
    typeof body.status === 'number' &&
    'title' in body &&
    typeof body.title === 'string' &&
    'code' in body &&
    typeof body.code === 'string' &&
    'requestId' in body &&
    typeof body.requestId === 'string'
  );
}
