import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { finalize } from 'rxjs';
import type { PortfolioLineResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { failureMessage } from '../../core/http/problem';
import { decimalValidator, integerValidator, normalizeDecimal } from '../../shared/format/decimal';

/** Changes the quantity and the average price of a line; closes with `true` once saved. */
@Component({
  selector: 'inv-line-dialog',
  imports: [
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    ReactiveFormsModule,
  ],
  templateUrl: './line-dialog.html',
  styleUrl: './line-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineDialog {
  private readonly api = inject(PortfolioService);
  private readonly dialog = inject<MatDialogRef<LineDialog, boolean>>(MatDialogRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  protected readonly line = inject<PortfolioLineResponse>(MAT_DIALOG_DATA);

  protected readonly form = this.formBuilder.group({
    quantity: this.formBuilder.control<number | null>(this.line.quantity, integerValidator),
    averagePrice: [
      this.line.averagePrice?.replace('.', ',') ?? '',
      decimalValidator('price', { positive: true }),
    ],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    const { quantity, averagePrice } = this.form.getRawValue();
    const price = averagePrice.trim() === '' ? undefined : normalizeDecimal(averagePrice, 'price');
    if (this.form.invalid || quantity === null || price === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api
      .updatePortfolioLine(this.line.id, {
        quantity,
        ...(price === undefined ? {} : { averagePrice: price }),
      })
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.dialog.close(true);
        },
        error: (error: unknown) => {
          this.error.set(failureMessage('La modification a échoué.', error));
        },
      });
  }
}
