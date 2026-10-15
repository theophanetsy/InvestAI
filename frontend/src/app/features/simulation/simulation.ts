import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'inv-simulation',
  template: `
    <h1>Simulation</h1>
    <p>Bientôt disponible (Phase 4).</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Simulation {}
