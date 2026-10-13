import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SystemStatusService } from './system-status.service';

describe('SystemStatusService', () => {
  let service: SystemStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SystemStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('fetches the backend status', async () => {
    const status = firstValueFrom(service.getStatus());

    http
      .expectOne('/api/v1/status')
      .flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });

    expect(await status).toEqual({
      application: 'investai-backend',
      serverTime: '2026-01-02T03:04:05Z',
    });
  });
});
