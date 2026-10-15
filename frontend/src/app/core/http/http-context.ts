import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Whether a 401 sends the user back to the login screen (`unauthorizedInterceptor`). */
export const REDIRECT_ON_UNAUTHORIZED = new HttpContextToken<boolean>(() => true);

/** Whether a network or server (5xx) error raises a notification (`serverErrorInterceptor`). */
export const NOTIFY_SERVER_ERRORS = new HttpContextToken<boolean>(() => true);

/** Context of a call that handles its own errors: no redirection, no notification. */
export function selfHandledErrors(): HttpContext {
  return new HttpContext().set(REDIRECT_ON_UNAUTHORIZED, false).set(NOTIFY_SERVER_ERRORS, false);
}
