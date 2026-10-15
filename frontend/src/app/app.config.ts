import { provideHttpClient } from '@angular/common/http';
import type { ApplicationConfig } from '@angular/core';
import { provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { provideApiClient } from './core/api/api-client';
import { withApiInterceptors } from './core/http/api-interceptors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withApiInterceptors()),
    provideApiClient(),
  ],
};
