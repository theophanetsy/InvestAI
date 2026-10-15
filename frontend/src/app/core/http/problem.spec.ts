import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import type { Problem } from '../../api/generated';
import { UNREACHABLE_MESSAGE, failureMessage, toProblem, withReference } from './problem';

describe('toProblem', () => {
  it('returns the problem+json body sent by the backend', () => {
    const body: Problem = {
      status: 401,
      title: 'Unauthorized',
      code: 'auth.invalid-credentials',
      requestId: 'req-1',
    };

    expect(toProblem(new HttpErrorResponse({ status: 401, error: body }))).toBe(body);
  });

  it('builds a problem for a network failure', () => {
    const error = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });

    expect(toProblem(error)).toEqual({
      status: 0,
      title: 'Network error',
      code: 'network.unavailable',
      requestId: '',
    });
  });

  it('builds a problem from the status and the request id header for a non-problem body', () => {
    const error = new HttpErrorResponse({
      status: 502,
      statusText: 'Bad Gateway',
      error: '<html>proxy error</html>',
      headers: new HttpHeaders({ 'X-Request-Id': 'req-2' }),
    });

    expect(toProblem(error)).toEqual({
      status: 502,
      title: 'HTTP 502',
      code: 'http.502',
      requestId: 'req-2',
    });
  });

  it('ignores a JSON body that lacks the problem fields', () => {
    const error = new HttpErrorResponse({ status: 500, error: { code: 'x', status: 500 } });

    expect(toProblem(error).code).toBe('http.500');
  });
});

describe('withReference', () => {
  const problem: Problem = { status: 500, title: 'Error', code: 'internal.error', requestId: 'r' };

  it('appends the request id for support', () => {
    expect(withReference('Erreur du serveur.', problem)).toBe('Erreur du serveur. Référence : r');
  });

  it('leaves the message alone without request id', () => {
    expect(withReference('Erreur du serveur.', { ...problem, requestId: '' })).toBe(
      'Erreur du serveur.',
    );
  });
});

describe('failureMessage', () => {
  it('says when the server cannot be reached', () => {
    expect(failureMessage('Échec.', new HttpErrorResponse({ status: 0 }))).toBe(
      UNREACHABLE_MESSAGE,
    );
  });

  it('gives the reference of a failed request', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: { status: 403, title: 'Forbidden', code: 'auth.forbidden', requestId: 'r3' },
    });

    expect(failureMessage('Échec.', error)).toBe('Échec. Référence : r3');
  });

  it('keeps the plain message for an error that is not an HTTP response', () => {
    expect(failureMessage('Échec.', new TypeError('bug'))).toBe('Échec.');
  });
});
