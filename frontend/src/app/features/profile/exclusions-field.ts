import { ChangeDetectionStrategy, Component, model, signal } from '@angular/core';
import type { MatChipInputEvent } from '@angular/material/chips';
import { MatChipGrid, MatChipInput, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';

/** Same limit as the backend (`InvestorProfile`); the length (100) is the input `maxlength`. */
export const MAX_EXCLUSIONS = 20;

const HINT = 'Facultatif. Tapez puis Entrée, par exemple « Tabac ». 20 au plus.';

/** Sectors or instruments to avoid, typed one by one as chips. */
@Component({
  selector: 'inv-exclusions-field',
  imports: [MatChipGrid, MatChipInput, MatChipRemove, MatChipRow, MatFormField, MatHint, MatLabel],
  templateUrl: './exclusions-field.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExclusionsField {
  readonly exclusions = model.required<readonly string[]>();

  /** Why the last entry was not added; the typed text is then kept. */
  protected readonly hint = signal(HINT);

  protected add(event: MatChipInputEvent): void {
    const value = event.value.trim();
    const current = this.exclusions();
    if (value === '') {
      return;
    }
    if (current.some((item) => item.toLowerCase() === value.toLowerCase())) {
      this.hint.set(`« ${value} » est déjà dans la liste.`);
      return;
    }
    if (current.length >= MAX_EXCLUSIONS) {
      this.hint.set('20 exclusions au plus : retirez-en une pour en ajouter une autre.');
      return;
    }
    this.exclusions.set([...current, value]);
    this.hint.set(HINT);
    event.chipInput.clear();
  }

  protected remove(exclusion: string): void {
    this.exclusions.update((current) => current.filter((item) => item !== exclusion));
    this.hint.set(HINT);
  }
}
