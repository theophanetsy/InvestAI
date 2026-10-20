import { expect, test } from '@playwright/test';
import { mockApi } from './support/api-mock';
import { mockDashboardApi } from './support/dashboard-mock';

test.describe('dashboard', () => {
  test('shows the indicators of the declared portfolio and their limits', async ({ page }) => {
    await mockApi(page, { loggedIn: true });
    await mockDashboardApi(page);

    await page.goto('/dashboard');

    await expect(page.getByTestId('card-total')).toHaveText(/^3.000,00.€$/);
    await expect(page.getByText('Cours au 16/10/2026')).toBeVisible();
    await expect(page.getByTestId('card-volatility')).toHaveText(/^16,20.%$/);
    await expect(page.getByTestId('card-max-drawdown')).toHaveText(/^-9,80.%$/);
    await expect(page.getByTestId('card-herfindahl')).toHaveText('0,5359');

    const warnings = page.getByTestId('dashboard-warnings');
    await expect(warnings).toContainText('Nouvelle cotée (historique insuffisant)');
    await expect(warnings).toContainText('Cours possiblement non ajustés');

    const lines = page.getByRole('table', { name: /Lignes du PEA/ });
    await expect(lines.getByRole('row', { name: /Air Liquide/ })).toContainText(/\+2,35.%/);
    await expect(lines.getByRole('row', { name: /Nouvelle cotée/ })).toContainText(
      'Non : historique insuffisant',
    );
    await expect(page.getByRole('table', { name: /Corrélations/ })).toContainText('0,42');
  });

  test('draws the one-year curve with a text alternative', async ({ page }) => {
    await mockApi(page, { loggedIn: true });
    await mockDashboardApi(page);
    await page.goto('/dashboard');

    const chart = page.getByRole('img', { name: /comme s’il avait été détenu depuis un an/ });
    await expect(chart).toBeVisible();
    await expect(chart).toHaveAccessibleDescription(/Base 100 le 21\/10\/2025/);
    await expect(chart.locator('path')).toHaveCount(3);
    await expect(page.getByText(/ETF MSCI World \(CW8\) : 103,15/)).toBeVisible();

    // The data table is reached and opened with the keyboard.
    const summary = page.getByText(/Données du graphique \(253 séances\)/);
    await summary.focus();
    await page.keyboard.press('Enter');
    const data = page.getByRole('table', { name: /Base 100, une ligne par séance/ });
    await expect(data).toBeVisible();
    // Session 120 has no close of the portfolio: a gap, never an interpolated value.
    await expect(data.getByRole('row', { name: /^18\/02\/2026/ })).toContainText('—');
  });

  for (const colorScheme of ['light', 'dark'] as const) {
    test(`keeps the curve readable in the ${colorScheme} theme`, async ({ page }) => {
      await page.emulateMedia({ colorScheme });
      await mockApi(page, { loggedIn: true });
      await mockDashboardApi(page);
      await page.goto('/dashboard');

      const colours = await page.getByTestId('performance-chart').evaluate((chart) => ({
        background: getComputedStyle(document.body).backgroundColor,
        lines: [...chart.querySelectorAll('path')].map((path) => getComputedStyle(path).stroke),
      }));

      expect(colours.lines).toHaveLength(3);
      expect(new Set(colours.lines).size).toBe(3);
      for (const stroke of colours.lines) {
        expect(stroke).not.toBe(colours.background);
      }
    });
  }

  test('invites to declare lines when the portfolio is empty', async ({ page }) => {
    await mockApi(page, { loggedIn: true });
    await page.goto('/dashboard');

    await expect(page.getByTestId('dashboard-empty')).toContainText('Aucune ligne déclarée');
    await page.getByRole('link', { name: 'Mon portefeuille' }).last().click();
    await expect(page).toHaveURL('/portefeuille');
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('keeps the dashboard within the screen width', async ({ page }) => {
      await mockApi(page, { loggedIn: true });
      await mockDashboardApi(page);

      await page.goto('/dashboard');

      await expect(page.getByTestId('performance-chart')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        390,
      );
    });
  });
});
