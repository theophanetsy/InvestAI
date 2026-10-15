import type { EnvironmentProviders } from '@angular/core';
import { provideApi } from '../../api/generated';

/**
 * Configures the generated API client (ADR-0004). The base path is empty: requests stay relative
 * (`/api/v1/...`), on the frontend origin (proxy in development). Without it, the generator falls
 * back to `http://localhost`.
 */
export function provideApiClient(): EnvironmentProviders {
  return provideApi('');
}
