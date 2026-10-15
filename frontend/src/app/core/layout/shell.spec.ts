import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { provideApiClient } from '../api/api-client';
import { AuthSession } from '../auth/auth-session';
import { Shell } from './shell';

describe('Shell', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Shell>;
  let element: HTMLElement;
  const snackBar = { open: vi.fn() };

  function configure(narrow: boolean): void {
    snackBar.open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient(),
        provideRouter([{ path: '**', children: [] }]),
        { provide: MatSnackBar, useValue: snackBar },
        {
          provide: BreakpointObserver,
          useValue: { observe: () => of({ matches: narrow, breakpoints: {} }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  async function render(): Promise<void> {
    const loaded = firstValueFrom(TestBed.inject(AuthSession).isAuthenticated());
    http.expectOne('/api/v1/auth/me').flush({ username: 'owner' });
    await loaded;

    fixture = TestBed.createComponent(Shell);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    for (const status of http.match('/api/v1/status')) {
      status.flush({ application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' });
    }
    await fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement | undefined {
    return [...element.querySelectorAll('button')].find((b) => b.textContent.trim() === label);
  }

  function navigationLinks(): HTMLAnchorElement[] {
    return [
      ...element.querySelectorAll<HTMLAnchorElement>(
        '[role="navigation"][aria-label="Navigation principale"] a',
      ),
    ];
  }

  function text(selector: string): string | undefined {
    return element.querySelector(selector)?.textContent.trim();
  }

  describe('on a wide screen', () => {
    beforeEach(() => {
      configure(false);
    });

    it('shows the app bar with the backend status and the user, in a banner', async () => {
      await render();

      const banner = element.querySelector('header');
      expect(banner?.textContent).toContain('InvestAI');
      expect(text('[data-testid="backend-status"]')).toBe('Backend connecté');
      expect(text('[data-testid="current-user"]')).toBe('owner');
      expect(button('Menu')).toBeUndefined();
    });

    it('links to every feature from a single, named navigation landmark', async () => {
      await render();

      expect(element.querySelectorAll('nav, [role="navigation"]')).toHaveLength(1);
      expect(
        navigationLinks().map((link) => [link.textContent.trim(), link.getAttribute('href')]),
      ).toEqual([
        ['Tableau de bord', '/dashboard'],
        ['Assistant', '/assistant'],
        ['Actu', '/actu'],
        ['Simulation', '/simulation'],
      ]);
    });

    it('logs out, then shows the login screen', async () => {
      await render();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      button('Se déconnecter')?.click();
      await fixture.whenStable();
      expect(button('Se déconnecter')?.disabled).toBe(true);
      http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

      expect(TestBed.inject(AuthSession).user()).toBeNull();
      expect(navigate).toHaveBeenCalledWith('/login');
    });

    it('stays logged in and says so when the logout fails', async () => {
      await render();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      button('Se déconnecter')?.click();
      http
        .expectOne('/api/v1/auth/logout')
        .flush(
          { status: 403, title: 'Forbidden', code: 'auth.forbidden', requestId: 'r9' },
          { status: 403, statusText: 'Forbidden' },
        );
      await fixture.whenStable();

      expect(TestBed.inject(AuthSession).user()).toEqual({ username: 'owner' });
      expect(navigate).not.toHaveBeenCalled();
      expect(snackBar.open).toHaveBeenCalledWith(
        'La déconnexion a échoué. Référence : r9',
        'Fermer',
      );
      expect(button('Se déconnecter')?.disabled).toBe(false);
    });
  });

  describe('on a narrow screen', () => {
    beforeEach(() => {
      configure(true);
    });

    it('keeps the app bar compact', async () => {
      await render();

      expect(element.querySelector('[data-testid="backend-status"]')).toBeNull();
      expect(element.querySelector('[data-testid="current-user"]')).toBeNull();
      expect(button('Se déconnecter')).toBeDefined();
    });

    it('hides the navigation behind a menu button, closed again after a choice', async () => {
      await render();
      const menu = button('Menu');
      expect(menu?.getAttribute('aria-expanded')).toBe('false');
      expect(element.querySelector('mat-sidenav')?.classList).toContain('mat-drawer-over');

      menu?.click();
      await fixture.whenStable();
      expect(menu?.getAttribute('aria-expanded')).toBe('true');

      navigationLinks()[0]?.click();
      await fixture.whenStable();
      expect(menu?.getAttribute('aria-expanded')).toBe('false');
    });
  });
});
