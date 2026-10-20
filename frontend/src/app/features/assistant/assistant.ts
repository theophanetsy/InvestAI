import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * No chat in the application (ADR-0010): advice happens in Claude (Desktop or Code), which reads
 * the profile, the portfolio and the indicators through the MCP server of InvestAI.
 */
@Component({
  selector: 'inv-assistant',
  templateUrl: './assistant.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Assistant {}
