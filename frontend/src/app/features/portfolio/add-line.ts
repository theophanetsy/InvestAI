import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroupDirective,
  NonNullableFormBuilder,
  ReactiveFormsModule,
} from '@angular/forms';
import type { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import type { Observable } from 'rxjs';
import { catchError, debounceTime, finalize, map, of, switchMap, tap } from 'rxjs';
import type { InstrumentSummaryResponse } from '../../api/generated';
import { MarketService, PortfolioService } from '../../api/generated';
import { inlineErrors } from '../../core/http/http-context';
import { failureMessage, toProblem } from '../../core/http/problem';
import { decimalValidator, integerValidator, normalizeDecimal } from '../../shared/format/decimal';
import { PeaEligibilityBadge } from '../../shared/pea-eligibility/pea-eligibility-badge';

const MIN_QUERY_LENGTH = 2;
const SEARCH_DELAY_MS = 300;

interface SearchState {
  readonly results: readonly InstrumentSummaryResponse[];
  readonly failed: boolean;
  readonly searched: boolean;
}

const IDLE: SearchState = { results: [], failed: false, searched: false };

/** Adds a line: search an instrument by name, ISIN or ticker, then copy the broker's figures. */
@Component({
  selector: 'inv-add-line',
  imports: [
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatButton,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
    PeaEligibilityBadge,
    ReactiveFormsModule,
  ],
  templateUrl: './add-line.html',
  styleUrl: './add-line.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddLine {
  private readonly portfolio = inject(PortfolioService);
  private readonly market = inject(MarketService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** Emitted once the line is stored, so that the page values the portfolio again. */
  readonly added = output();

  protected readonly query = new FormControl<string | InstrumentSummaryResponse>('', {
    nonNullable: true,
  });
  protected readonly form = this.formBuilder.group({
    quantity: this.formBuilder.control<number | null>(null, integerValidator),
    averagePrice: ['', decimalValidator('price', { positive: true })],
  });
  protected readonly selected = signal<InstrumentSummaryResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly search = toSignal(
    this.query.valueChanges.pipe(
      // Typing again drops the instrument chosen before.
      tap((value) => {
        if (typeof value === 'string') {
          this.selected.set(null);
        }
      }),
      map((value) => (typeof value === 'string' ? value.trim() : '')),
      debounceTime(SEARCH_DELAY_MS),
      switchMap((query) => (query.length < MIN_QUERY_LENGTH ? of(IDLE) : this.find(query))),
    ),
    { initialValue: IDLE },
  );

  protected readonly searchHint = computed(() => {
    const { failed, searched, results } = this.search();
    if (failed) {
      return 'Recherche impossible pour le moment.';
    }
    return searched && results.length === 0
      ? 'Aucune valeur trouvée.'
      : 'Au moins 2 caractères, par exemple « air » ou FR0000120073.';
  });
  /** `resetForm()` also clears `submitted`: `FormGroup.reset()` would leave errors displayed. */
  private readonly formDirective = viewChild.required(FormGroupDirective);

  protected displayName(value: string | InstrumentSummaryResponse | null): string {
    return typeof value === 'string' || value === null ? (value ?? '') : value.name;
  }

  protected choose(event: MatAutocompleteSelectedEvent): void {
    const instrument: unknown = event.option.value;
    if (isInstrument(instrument)) {
      this.selected.set(instrument);
      this.error.set(null);
    }
  }

  protected submit(): void {
    const instrument = this.selected();
    const { quantity, averagePrice } = this.form.getRawValue();
    const price = averagePrice.trim() === '' ? undefined : normalizeDecimal(averagePrice, 'price');
    if (instrument === null || this.form.invalid || quantity === null || price === null) {
      this.form.markAllAsTouched();
      this.error.set(instrument === null ? 'Choisissez d’abord une valeur dans la liste.' : null);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.portfolio
      .addPortfolioLine(
        {
          instrumentId: instrument.id,
          quantity,
          ...(price === undefined ? {} : { averagePrice: price }),
        },
        'body',
        false,
        { context: inlineErrors() },
      )
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.reset();
          this.added.emit();
        },
        error: (error: unknown) => {
          this.error.set(addErrorMessage(error));
        },
      });
  }

  private find(query: string): Observable<SearchState> {
    // The search shows its own failure: no notification on top of it.
    return this.market.searchInstruments(query, 'body', false, { context: inlineErrors() }).pipe(
      map((results): SearchState => ({ results, failed: false, searched: true })),
      catchError(() => of<SearchState>({ results: [], failed: true, searched: true })),
    );
  }

  private reset(): void {
    this.selected.set(null);
    this.query.setValue('');
    this.formDirective().resetForm();
  }
}

function isInstrument(value: unknown): value is InstrumentSummaryResponse {
  return typeof value === 'object' && value !== null && 'id' in value && 'isin' in value;
}

function addErrorMessage(error: unknown): string {
  const code = error instanceof HttpErrorResponse ? toProblem(error).code : '';
  switch (code) {
    case 'portfolio.line.duplicate-instrument':
      return 'Cette valeur est déjà dans le portefeuille : modifiez sa ligne.';
    case 'portfolio.line.currency-not-supported':
      return 'Seules les valeurs cotées en euros peuvent être ajoutées.';
    case 'instrument.not-found':
      return 'Cette valeur n’existe plus dans le référentiel.';
    default:
      return failureMessage('L’ajout de la ligne a échoué.', error);
  }
}
