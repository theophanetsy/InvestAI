import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { SystemService } from '../../api/generated';
import { selfHandledErrors } from '../http/http-context';

type BackendState = 'loading' | 'up' | 'down';

/** Whether the backend answers, checked once; displays its own failure (no notification). */
@Component({
  selector: 'inv-backend-status',
  templateUrl: './backend-status.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BackendStatus {
  protected readonly state = toSignal(
    inject(SystemService)
      .getStatus('body', false, { context: selfHandledErrors() })
      .pipe(
        map((): BackendState => 'up'),
        catchError(() => of<BackendState>('down')),
      ),
    { initialValue: 'loading' },
  );
}
