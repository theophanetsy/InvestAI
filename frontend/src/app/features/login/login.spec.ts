import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideApiClient } from '../../core/api/api-client';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Login>;
  let element: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
  });

  /** The input named by `label`, through the label's `for` attribute (accessible name). */
  function field(label: string): HTMLInputElement {
    const labelElement = [...element.querySelectorAll('label')].find((candidate) =>
      candidate.textContent.trim().startsWith(label),
    );
    const input =
      labelElement && element.querySelector<HTMLInputElement>(`#${labelElement.htmlFor}`);
    if (!input) {
      throw new Error(`No input labelled ${label}`);
    }
    return input;
  }

  function type(label: string, value: string): void {
    const input = field(label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submitButton(): HTMLButtonElement {
    const button = element.querySelector<HTMLButtonElement>('button[type="submit"]');
    if (!button) {
      throw new Error('No submit button');
    }
    return button;
  }

  async function submit(username = 'owner', password = 'secret'): Promise<void> {
    type('Identifiant', username);
    type('Mot de passe', password);
    submitButton().click();
    await fixture.whenStable();
  }

  function alertText(): string | undefined {
    return element.querySelector('[role="alert"]')?.textContent.trim();
  }

  it('labels both fields, with the autocomplete hints of a login form', () => {
    expect(field('Identifiant').autocomplete).toBe('username');
    expect(field('Mot de passe').type).toBe('password');
    expect(field('Mot de passe').autocomplete).toBe('current-password');
  });

  it('requires both fields before calling the backend', async () => {
    submitButton().click();
    await fixture.whenStable();

    expect(element.querySelectorAll('mat-error')).toHaveLength(2);
    http.expectNone('/api/v1/auth/login');
  });

  it('logs in, then goes back to the requested page', async () => {
    fixture.componentRef.setInput('returnUrl', '/simulation');
    await submit();

    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.body).toEqual({ username: 'owner', password: 'secret' });
    expect(submitButton().disabled).toBe(true);
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/me').flush({ username: 'owner' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/simulation');
    expect(submitButton().disabled).toBe(false);
  });

  it('goes to the dashboard when the requested page is not an internal path', async () => {
    fixture.componentRef.setInput('returnUrl', '//evil.example');
    await submit();

    http.expectOne('/api/v1/auth/login').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/me').flush({ username: 'owner' });

    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('says when the credentials are wrong', async () => {
    await submit('owner', 'wrong');

    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { status: 401, title: 'Unauthorized', code: 'auth.invalid-credentials', requestId: 'r1' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(alertText()).toBe('Identifiant ou mot de passe incorrect.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the reference of any other error', async () => {
    await submit();

    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { status: 500, title: 'Internal Server Error', code: 'internal.error', requestId: 'r2' },
        { status: 500, statusText: 'Internal Server Error' },
      );
    await fixture.whenStable();

    expect(alertText()).toBe('La connexion a échoué. Référence : r2');
  });

  it('says when the server cannot be reached', async () => {
    await submit();

    http.expectOne('/api/v1/auth/login').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(alertText()).toBe('Serveur injoignable. Vérifiez votre connexion et réessayez.');
  });
});
