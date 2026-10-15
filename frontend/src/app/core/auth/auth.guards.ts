import { inject } from '@angular/core';
import type { CanActivateFn } from '@angular/router';
import { Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthSession } from './auth-session';
import { HOME_URL } from './return-url';

/** Pages of the application: anonymous users go to the login screen, then come back. */
export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  return inject(AuthSession)
    .isAuthenticated()
    .pipe(
      map(
        (authenticated) =>
          authenticated ||
          router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
      ),
    );
};

/** Login screen: a logged-in user has nothing to do there. */
export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthSession)
    .isAuthenticated()
    .pipe(map((authenticated) => (authenticated ? router.parseUrl(HOME_URL) : true)));
};
