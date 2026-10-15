import type { HttpFeature, HttpFeatureKind } from '@angular/common/http';
import { withInterceptors } from '@angular/common/http';
import { csrfRetryInterceptor } from './csrf-retry.interceptor';
import { serverErrorInterceptor } from './server-error.interceptor';
import { unauthorizedInterceptor } from './unauthorized.interceptor';

/**
 * Error handling of every API call, outermost first: the CSRF retry runs closest to the backend,
 * so the notification and the redirection only see the final outcome.
 */
export function withApiInterceptors(): HttpFeature<HttpFeatureKind.Interceptors> {
  return withInterceptors([serverErrorInterceptor, unauthorizedInterceptor, csrfRetryInterceptor]);
}
