import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SystemService } from '../../api/generated';
import { provideApiClient } from './api-client';

describe('provideApiClient', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiClient()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('sends generated client requests to the same origin, with relative URLs', async () => {
    const status = firstValueFrom(TestBed.inject(SystemService).getStatus());

    const request = http.expectOne('/api/v1/status');
    expect(request.request.method).toBe('GET');
    request.flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });

    expect(await status).toEqual({
      application: 'investai-backend',
      serverTime: '2026-01-02T03:04:05Z',
    });
  });
});
