import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { finalize } from 'rxjs';
import type { InstrumentSummaryResponse } from '../../api/generated';
import {
  MarketService,
  PeaEligibilityRequestStatusEnum,
  PeaEligibilityResponseSourceEnum,
  PeaEligibilityResponseStatusEnum,
} from '../../api/generated';

/** One enum per generated DTO: the typed mapping fails to compile if a status is added. */
const REQUEST_STATUS: Record<PeaEligibilityResponseStatusEnum, PeaEligibilityRequestStatusEnum> = {
  [PeaEligibilityResponseStatusEnum.Eligible]: PeaEligibilityRequestStatusEnum.Eligible,
  [PeaEligibilityResponseStatusEnum.NotEligible]: PeaEligibilityRequestStatusEnum.NotEligible,
  [PeaEligibilityResponseStatusEnum.Unknown]: PeaEligibilityRequestStatusEnum.Unknown,
};
import { inlineErrors } from '../../core/http/http-context';
import { failureMessage } from '../../core/http/problem';
import { PeaEligibilityBadge } from '../../shared/pea-eligibility/pea-eligibility-badge';

/**
 * The owner corrects the PEA eligibility of an instrument, e.g. after checking with the broker;
 * the correction is never overwritten by the rule. Closes with `true` once saved.
 */
@Component({
  selector: 'inv-eligibility-dialog',
  imports: [
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatRadioButton,
    MatRadioGroup,
    PeaEligibilityBadge,
    ReactiveFormsModule,
  ],
  templateUrl: './eligibility-dialog.html',
  styleUrl: './eligibility-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EligibilityDialog {
  private readonly market = inject(MarketService);
  private readonly dialog = inject<MatDialogRef<EligibilityDialog, boolean>>(MatDialogRef);
  protected readonly instrument = inject<InstrumentSummaryResponse>(MAT_DIALOG_DATA);

  protected readonly choices = [
    { value: PeaEligibilityRequestStatusEnum.Eligible, label: 'Éligible au PEA' },
    { value: PeaEligibilityRequestStatusEnum.NotEligible, label: 'Non éligible au PEA' },
    { value: PeaEligibilityRequestStatusEnum.Unknown, label: 'Je ne sais pas' },
  ] as const;
  /** The owner's previous answer is preselected; the rule's guess is not. */
  protected readonly status = new FormControl<PeaEligibilityRequestStatusEnum | null>(
    this.instrument.peaEligibility.source === PeaEligibilityResponseSourceEnum.Owner
      ? REQUEST_STATUS[this.instrument.peaEligibility.status]
      : null,
  );
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    const status = this.status.value;
    if (status === null) {
      this.error.set('Choisissez une réponse.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.dialog.disableClose = true;
    this.market
      .correctPeaEligibility(this.instrument.id, { status }, 'body', false, {
        context: inlineErrors(),
      })
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.dialog.disableClose = false;
        }),
      )
      .subscribe({
        next: () => {
          this.dialog.close(true);
        },
        error: (error: unknown) => {
          this.error.set(failureMessage('La correction a échoué.', error));
        },
      });
  }
}
