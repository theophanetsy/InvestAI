import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { finalize } from 'rxjs';
import type { CashResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { failureMessage } from '../../core/http/problem';
import { decimalValidator, normalizeDecimal } from '../../shared/format/decimal';

/** Cash available on the PEA, as shown by the broker (zero or more, optional). */
@Component({
  selector: 'inv-cash-form',
  imports: [MatButton, MatError, MatFormField, MatHint, MatInput, MatLabel, ReactiveFormsModule],
  templateUrl: './cash-form.html',
  styleUrl: './cash-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CashForm {
  private readonly api = inject(PortfolioService);

  /** The declared cash, absent until the owner declares it. */
  readonly cash = input<CashResponse>();
  /** Emitted once the cash is stored. */
  readonly saved = output();

  protected readonly form = new FormGroup({
    amount: new FormControl('', {
      nonNullable: true,
      validators: decimalValidator('amount', { positive: false }),
    }),
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.form.controls.amount.setValue(this.cash()?.amount.replace('.', ',') ?? '');
    });
  }

  protected submit(): void {
    const amount = normalizeDecimal(this.form.controls.amount.value, 'amount');
    if (amount === null) {
      this.form.markAllAsTouched();
      this.error.set('Montant invalide : zéro ou plus, deux décimales au plus.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .setPortfolioCash({ amount })
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.saved.emit();
        },
        error: (error: unknown) => {
          this.error.set(failureMessage('L’enregistrement des espèces a échoué.', error));
        },
      });
  }
}
