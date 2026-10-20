import { HttpErrorResponse } from '@angular/common/http';
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import type { Observable } from 'rxjs';
import { filter, finalize, switchMap } from 'rxjs';
import type { PortfolioLineResponse, PortfolioResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { inlineErrors } from '../../core/http/http-context';
import { failureMessage, toProblem } from '../../core/http/problem';
import type { ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { AddLine } from './add-line';
import { EligibilityDialog } from './eligibility-dialog';
import { LineDialog } from './line-dialog';
import { PortfolioSummary } from './portfolio-summary';
import { PortfolioTable } from './portfolio-table';

/**
 * "Mon portefeuille": the lines of the PEA as declared from the broker, valued by the backend on
 * the latest known closes. Every figure comes from the API as a decimal string: nothing is
 * computed here, only formatted.
 */
@Component({
  selector: 'inv-portfolio',
  imports: [AddLine, MatButton, MatProgressBar, PortfolioSummary, PortfolioTable],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Portfolio {
  private readonly api = inject(PortfolioService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly portfolio = signal<PortfolioResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    this.load();
  }

  /** Values the portfolio again; `then` runs once it is displayed. */
  protected load(then?: () => void): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getPortfolio('body', false, { context: inlineErrors() })
      .pipe(
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: (portfolio) => {
          this.portfolio.set(portfolio);
          then?.();
        },
        error: (error: unknown) => {
          this.loadError.set(failureMessage('Le portefeuille n’a pas pu être chargé.', error));
        },
      });
  }

  /** Announces a change made below the table (the add form, the cash), then values again. */
  protected changed(message: string): void {
    this.notify(message);
    this.load();
  }

  protected edit(line: PortfolioLineResponse): void {
    this.afterSave(this.dialog.open(LineDialog, { data: line }).afterClosed(), 'Ligne modifiée.');
  }

  protected correctEligibility(line: PortfolioLineResponse): void {
    this.afterSave(
      this.dialog.open(EligibilityDialog, { data: line.instrument }).afterClosed(),
      'Éligibilité PEA corrigée.',
    );
  }

  protected remove(line: PortfolioLineResponse): void {
    const data: ConfirmDialogData = {
      title: 'Supprimer la ligne ?',
      message: `${line.instrument.name} sera retirée du portefeuille déclaré. La valeur reste suivie.`,
      confirmLabel: 'Supprimer',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() =>
          this.api.deletePortfolioLine(line.id, 'body', false, { context: inlineErrors() }),
        ),
      )
      .subscribe({
        next: () => {
          this.notify('Ligne supprimée.');
          // The focused button disappeared with its row: back to the top of the page.
          this.load(() => {
            this.heading().nativeElement.focus();
          });
        },
        error: (error: unknown) => {
          this.snackBar.open(failureMessage('La suppression a échoué.', error), 'Fermer');
          if (
            error instanceof HttpErrorResponse &&
            toProblem(error).code === 'portfolio.line.not-found'
          ) {
            this.load();
          }
        },
      });
  }

  /** The dialogs save by themselves and close with `true` once done. */
  private afterSave(closed: Observable<unknown>, message: string): void {
    closed.pipe(filter((saved) => saved === true)).subscribe({
      next: () => {
        this.changed(message);
      },
      error: () => {
        // A dialog never fails: the dialogs show their own errors.
      },
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'Fermer', { duration: 4000 });
  }
}
