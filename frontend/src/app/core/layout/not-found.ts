import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HOME_URL } from '../auth/return-url';

@Component({
  selector: 'inv-not-found',
  imports: [RouterLink],
  template: `
    <h1>Page introuvable</h1>
    <p>Cette page n'existe pas.</p>
    <a [routerLink]="home">Retour au tableau de bord</a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFound {
  protected readonly home = HOME_URL;
}
