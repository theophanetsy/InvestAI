import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SystemStatusService } from './core/system/system-status.service';

type BackendState = 'loading' | 'up' | 'down';

@Component({
  selector: 'inv-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly backendState = toSignal(
    inject(SystemStatusService)
      .getStatus()
      .pipe(
        map((): BackendState => 'up'),
        catchError(() => of<BackendState>('down')),
      ),
    { initialValue: 'loading' },
  );
}
