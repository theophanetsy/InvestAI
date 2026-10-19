import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import type { Observable } from 'rxjs';
import { filter, finalize, switchMap } from 'rxjs';
import type { PortfolioLineResponse, PortfolioResponse } from '../../api/generated';
import { PortfolioService } from '../../api/generated';
import { failureMessage } from '../../core/http/problem';
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

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getPortfolio()
      .pipe(
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: (portfolio) => {
          this.portfolio.set(portfolio);
        },
        error: (error: unknown) => {
          this.loadError.set(failureMessage('Le portefeuille n’a pas pu être chargé.', error));
        },
      });
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
        switchMap(() => this.api.deletePortfolioLine(line.id)),
      )
      .subscribe({
        next: () => {
          this.snackBar.open('Ligne supprimée.', 'Fermer', { duration: 4000 });
          this.load();
        },
        error: (error: unknown) => {
          this.snackBar.open(failureMessage('La suppression a échoué.', error), 'Fermer');
        },
      });
  }

  /** The dialogs save by themselves and close with `true` once done. */
  private afterSave(closed: Observable<unknown>, message: string): void {
    closed.pipe(filter((saved) => saved === true)).subscribe(() => {
      this.snackBar.open(message, 'Fermer', { duration: 4000 });
      this.load();
    });
  }
}
