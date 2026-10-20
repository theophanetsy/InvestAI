import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { startWith, switchMap } from 'rxjs';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { RiskProfileResponse } from '../../api/generated';

/** The four risk profiles as radio buttons, with the description of the chosen one. */
@Component({
  selector: 'inv-risk-profile-field',
  imports: [MatRadioButton, MatRadioGroup, ReactiveFormsModule],
  templateUrl: './risk-profile-field.html',
  styleUrl: './risk-profile-field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskProfileField {
  /** Code of the chosen profile, `''` until chosen. */
  readonly control = input.required<FormControl<string>>();
  readonly options = input.required<readonly RiskProfileResponse[]>();

  private readonly value = toSignal(
    toObservable(this.control).pipe(
      switchMap((control) => control.valueChanges.pipe(startWith(control.value))),
    ),
    { initialValue: '' },
  );
  protected readonly chosen = computed(() =>
    this.options().find((option) => {
      const code: string = option.code;
      return code === this.value();
    }),
  );
}
