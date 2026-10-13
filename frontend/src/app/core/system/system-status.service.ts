import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import type { Observable } from 'rxjs';

export interface SystemStatus {
  readonly application: string;
  readonly serverTime: string;
}

@Service()
export class SystemStatusService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/v1/status');
  }
}
