import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SystemService } from './api/generated';

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
    inject(SystemService)
      .getStatus()
      .pipe(
        map((): BackendState => 'up'),
        catchError(() => of<BackendState>('down')),
      ),
    { initialValue: 'loading' },
  );
}
