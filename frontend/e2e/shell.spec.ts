import { expect, test } from '@playwright/test';
import { API, mockApi } from './support/api-mock';

test.describe('application shell', () => {
  test('opens the dashboard with the backend connected', async ({ page }) => {
    await mockApi(page, { loggedIn: true });

    await page.goto('/');

    await expect(page).toHaveURL('/dashboard');
    await expect(page).toHaveTitle('Tableau de bord · InvestAI');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tableau de bord');
    await expect(page.getByTestId('backend-status')).toHaveText('Backend connecté');
    await expect(page.getByTestId('current-user')).toHaveText('owner');
  });

  test('shows the backend as unavailable, without a notification', async ({ page, baseURL }) => {
    await mockApi(page, { loggedIn: true, statusCode: 503 });
    const statusRequest = page.waitForRequest(
      (request) => request.url() === new URL(API.status, baseURL).href,
    );

    await page.goto('/dashboard');

    await statusRequest;
    await expect(page.getByTestId('backend-status')).toHaveText('Backend indisponible');
    // Checked once the failure is displayed: a notification would already be there.
    expect(await page.getByText('Erreur du serveur').count()).toBe(0);
  });

  test('navigates between features and marks the current page', async ({ page }) => {
    await mockApi(page, { loggedIn: true });
    await page.goto('/dashboard');
    const navigation = page.getByRole('navigation', { name: 'Navigation principale' });

    await navigation.getByRole('link', { name: 'Actu' }).click();

    await expect(page).toHaveURL('/actu');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Actu');
    await expect(navigation.getByRole('link', { name: 'Actu' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  test('shows a not found page for an unknown address', async ({ page }) => {
    await mockApi(page, { loggedIn: true });

    await page.goto('/nope');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Page introuvable');
    await page.getByRole('link', { name: 'Retour au tableau de bord' }).click();
    await expect(page).toHaveURL('/dashboard');
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('opens the navigation from the menu button', async ({ page }) => {
      await mockApi(page, { loggedIn: true });
      await page.goto('/dashboard');
      const link = page.getByRole('link', { name: 'Simulation' });
      await expect(link).toBeHidden();
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        390,
      );

      await page.getByRole('button', { name: 'Menu' }).click();
      await link.click();

      await expect(page).toHaveURL('/simulation');
      await expect(link).toBeHidden();
    });
  });
});
