import type { Locator, Page } from '@playwright/test';
import { expect } from '@playwright/test';

/** Presses Tab until the target has the focus, as a keyboard user would. */
export async function tabTo(page: Page, target: Locator, maxPresses = 20): Promise<void> {
  for (let presses = 0; presses < maxPresses; presses++) {
    await page.keyboard.press('Tab');
    if (await target.evaluate((element) => element === document.activeElement)) {
      return;
    }
  }
  throw new Error(`Not reached with ${String(maxPresses)} Tab presses`);
}

/**
 * Whether the focused element draws a Material focus ring: the `::before` of a
 * `.mat-focus-indicator` (on the element or inside it) is rendered, with a visible border.
 */
export async function expectFocusRing(target: Locator): Promise<void> {
  const drawn = await target.evaluate((element) =>
    [element, ...element.querySelectorAll('.mat-focus-indicator')]
      .filter((candidate) => candidate.classList.contains('mat-focus-indicator'))
      .some((candidate) => {
        const ring = getComputedStyle(candidate, '::before');
        return (
          ring.content !== 'none' && ring.display !== 'none' && parseFloat(ring.borderTopWidth) > 0
        );
      }),
  );
  expect(drawn, 'focus ring drawn').toBe(true);
}

/** The page does not scroll: its content fits the viewport in both directions. */
export async function expectNoScroll(page: Page): Promise<void> {
  const size = await page.evaluate(() => ({
    width: document.documentElement.scrollWidth,
    height: document.documentElement.scrollHeight,
    viewportWidth: window.innerWidth,
    viewportHeight: window.innerHeight,
  }));
  expect(size.width).toBeLessThanOrEqual(size.viewportWidth);
  expect(size.height).toBeLessThanOrEqual(size.viewportHeight);
}

/** WCAG relative luminance of a computed `rgb(r, g, b)` colour. */
function luminance(colour: string): number {
  const channels = (colour.match(/[0-9]+(?:[.][0-9]+)?/g) ?? []).slice(0, 3).map(Number);
  const [red = 0, green = 0, blue = 0] = channels.map((channel) => {
    const value = channel / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

/** WCAG contrast ratio between two computed colours (1 to 21). */
export function contrast(first: string, second: string): number {
  const [light, dark] = [luminance(first), luminance(second)].sort((a, b) => b - a);
  return ((light ?? 0) + 0.05) / ((dark ?? 0) + 0.05);
}
