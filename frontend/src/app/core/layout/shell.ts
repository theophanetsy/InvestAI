import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatListItem, MatNavList } from '@angular/material/list';
import type { MatSidenav } from '@angular/material/sidenav';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize, map } from 'rxjs';
import { AuthSession } from '../auth/auth-session';
import { failureMessage } from '../http/problem';
import { BackendStatus } from './backend-status';

interface NavigationLink {
  readonly path: string;
  readonly label: string;
}

/** Layout of the logged-in application: app bar, navigation, then the current page. */
@Component({
  selector: 'inv-shell',
  imports: [
    BackendStatus,
    MatButton,
    MatListItem,
    MatNavList,
    MatSidenavModule,
    MatToolbar,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly links: readonly NavigationLink[] = [
    { path: '/dashboard', label: 'Tableau de bord' },
    { path: '/assistant', label: 'Assistant' },
    { path: '/actu', label: 'Actu' },
    { path: '/simulation', label: 'Simulation' },
  ];
  protected readonly user = this.session.user;
  protected readonly loggingOut = signal(false);
  /** Narrow screen: the navigation slides over the page, behind a "Menu" button; compact app bar. */
  protected readonly narrow = toSignal(
    this.breakpoints.observe(Breakpoints.XSmall).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected navigated(drawer: MatSidenav): void {
    if (this.narrow()) {
      void drawer.close();
    }
  }

  protected logout(): void {
    this.loggingOut.set(true);
    this.session
      .logout()
      .pipe(
        finalize(() => {
          this.loggingOut.set(false);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/login');
        },
        error: (error: unknown) => {
          this.snackBar.open(failureMessage('La déconnexion a échoué.', error), 'Fermer');
        },
      });
  }
}
