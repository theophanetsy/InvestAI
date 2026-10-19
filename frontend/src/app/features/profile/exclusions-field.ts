import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import type { MatChipInputEvent } from '@angular/material/chips';
import { MatChipGrid, MatChipInput, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';

/** Same limit as the backend (`InvestorProfile`); the length (100) is the input `maxlength`. */
export const MAX_EXCLUSIONS = 20;

/** Sectors or instruments to avoid, typed one by one as chips. */
@Component({
  selector: 'inv-exclusions-field',
  imports: [MatChipGrid, MatChipInput, MatChipRemove, MatChipRow, MatFormField, MatHint, MatLabel],
  templateUrl: './exclusions-field.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExclusionsField {
  readonly exclusions = model.required<readonly string[]>();

  protected add(event: MatChipInputEvent): void {
    const value = event.value.trim();
    const current = this.exclusions();
    if (value !== '' && current.length < MAX_EXCLUSIONS && !current.includes(value)) {
      this.exclusions.set([...current, value]);
    }
    event.chipInput.clear();
  }

  protected remove(exclusion: string): void {
    this.exclusions.update((current) => current.filter((item) => item !== exclusion));
  }
}
