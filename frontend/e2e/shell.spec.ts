import { expect, test } from '@playwright/test';

test.describe('application shell', () => {
  test('shows the backend as connected when the API answers', async ({ page }) => {
    await page.route('**/api/v1/status', (route) =>
      route.fulfill({
        json: { application: 'investai-backend', serverTime: '2026-01-02T03:04:05Z' },
      }),
    );

    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('InvestAI');
    await expect(page.getByTestId('backend-status')).toHaveText('Backend connecté');
  });

  test('shows the backend as unavailable when the API fails', async ({ page }) => {
    await page.route('**/api/v1/status', (route) => route.fulfill({ status: 503 }));

    await page.goto('/');

    await expect(page.getByTestId('backend-status')).toHaveText('Backend indisponible');
  });
});
