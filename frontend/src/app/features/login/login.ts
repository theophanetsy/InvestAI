import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth/auth-session';
import { safeReturnUrl } from '../../core/auth/return-url';
import { failureMessage, toProblem } from '../../core/http/problem';

/** Login screen; goes back to the page that required a session (`returnUrl` query parameter). */
@Component({
  selector: 'inv-login',
  imports: [MatButton, MatError, MatFormField, MatInput, MatLabel, ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** Bound from the query parameter by the router (`withComponentInputBinding`). */
  readonly returnUrl = input<string>();

  /** Both fields are required by the template's `required` attribute (`RequiredValidator`). */
  protected readonly form = this.formBuilder.group({ username: '', password: '' });
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.pending.set(true);
    this.error.set(null);
    this.session
      .login(this.form.getRawValue())
      .pipe(
        finalize(() => {
          this.pending.set(false);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
        },
        error: (error: unknown) => {
          this.error.set(loginErrorMessage(error));
        },
      });
  }
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && toProblem(error).code === 'auth.invalid-credentials') {
    return 'Identifiant ou mot de passe incorrect.';
  }
  return failureMessage('La connexion a échoué.', error);
}
