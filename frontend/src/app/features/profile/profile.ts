import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import type { Observable } from 'rxjs';
import { catchError, finalize, forkJoin, of, throwError } from 'rxjs';
import type { ProfileRequest, ProfileResponse, RiskProfileResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { inlineErrors } from '../../core/http/http-context';
import { failureMessage, toProblem } from '../../core/http/problem';
import { decimalValidator, integerValidator, normalizeDecimal } from '../../shared/format/decimal';
import { ExclusionsField } from './exclusions-field';
import { RiskProfileField } from './risk-profile-field';
import { toRequestRiskProfile } from './risk-profile';

/** Today in the browser's time zone, as an ISO date (`2026-10-19`). */
function today(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function notInFuture(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value === 'string' && value !== '' && value > today() ? { future: true } : null;
}

/** "Mon profil": what the owner wants, read by the advice agent (phase 3). */
@Component({
  selector: 'inv-profile',
  imports: [
    ExclusionsField,
    MatButton,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatProgressBar,
    ReactiveFormsModule,
    RiskProfileField,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Profile {
  private readonly api = inject(PortfolioService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly maxDate = today();
  protected readonly form = this.formBuilder.group({
    amountToInvest: ['', decimalValidator('amount', { positive: true })],
    horizonYears: this.formBuilder.control<number | null>(null, integerValidator),
    riskProfile: '',
    objectives: '',
    peaOpenedOn: ['', notInFuture],
  });
  protected readonly exclusions = signal<readonly string[]>([]);
  protected readonly riskProfiles = signal<readonly RiskProfileResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  /** Always in the DOM (live region): only its text changes. */
  protected readonly savedText = signal('');
  protected readonly saveError = signal<string | null>(null);
  private readonly exists = signal(false);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      riskProfiles: this.api.listRiskProfiles('body', false, { context: inlineErrors() }),
      profile: this.api
        .getProfile('body', false, { context: inlineErrors() })
        .pipe(catchError(notFoundAsNull)),
    })
      .pipe(
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: ({ riskProfiles, profile }) => {
          this.riskProfiles.set(riskProfiles);
          this.fill(profile);
        },
        error: (error: unknown) => {
          this.loadError.set(failureMessage('Le profil n’a pas pu être chargé.', error));
        },
      });
  }

  protected stateText(): string {
    return this.exists()
      ? 'Modifiez votre profil puis enregistrez-le.'
      : 'Aucun profil enregistré : remplissez le formulaire ci-dessous.';
  }

  protected amountError(): string {
    const control = this.form.controls.amountToInvest;
    if (control.hasError('required')) {
      return 'La somme à placer est obligatoire.';
    }
    return control.hasError('positive')
      ? 'La somme doit être supérieure à zéro.'
      : 'Montant invalide : chiffres et deux décimales au plus.';
  }

  protected submit(): void {
    const request = this.request();
    if (this.form.invalid || request === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.savedText.set('');
    this.saveError.set(null);
    this.api
      .saveProfile(request, 'body', false, { context: inlineErrors() })
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: (profile) => {
          this.fill(profile);
          this.savedText.set('Profil enregistré.');
        },
        error: (error: unknown) => {
          this.saveError.set(saveErrorMessage(error));
        },
      });
  }

  private request(): ProfileRequest | null {
    const value = this.form.getRawValue();
    const amount = normalizeDecimal(value.amountToInvest, 'amount');
    const riskProfile = toRequestRiskProfile(value.riskProfile);
    if (amount === null || riskProfile === null || value.horizonYears === null) {
      return null;
    }
    return {
      amountToInvest: amount,
      horizonYears: value.horizonYears,
      riskProfile,
      exclusions: [...this.exclusions()],
      ...(value.objectives.trim() === '' ? {} : { objectives: value.objectives }),
      ...(value.peaOpenedOn === '' ? {} : { peaOpenedOn: value.peaOpenedOn }),
    };
  }

  private fill(profile: ProfileResponse | null): void {
    this.exists.set(profile !== null);
    if (profile === null) {
      return;
    }
    this.form.setValue({
      amountToInvest: profile.amountToInvest.replace('.', ','),
      horizonYears: profile.horizonYears,
      riskProfile: profile.riskProfile,
      objectives: profile.objectives ?? '',
      peaOpenedOn: profile.peaOpenedOn ?? '',
    });
    this.exclusions.set(profile.exclusions);
  }
}

function notFoundAsNull(error: unknown): Observable<null> {
  return error instanceof HttpErrorResponse && toProblem(error).code === 'profile.not-found'
    ? of(null)
    : throwError(() => error);
}

function saveErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && toProblem(error).code === 'validation.failed') {
    return 'Certaines valeurs ont été refusées par le serveur : vérifiez le formulaire.';
  }
  return failureMessage('L’enregistrement du profil a échoué.', error);
}
