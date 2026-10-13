# Frontend — consignes

Angular 22 · TypeScript 6 strict · zoneless · Vitest · Playwright. Node 24 (`.nvmrc`).

## Structure

```
src/app/
  core/            services transverses (HTTP, erreurs, auth), singletons
  shared/          composants/pipes UI réutilisables, sans logique métier
  features/<nom>/  dashboard, chat, news, simulation — chargés en lazy via le router
  api/generated/   client OpenAPI généré (ne jamais éditer à la main)
```

## Règles de code

- Composants **standalone**, `ChangeDetectionStrategy.OnPush`, état via **signals**
  (`signal`, `computed`, `toSignal`). Pas de `subscribe` manuel dans les composants sauf nécessité
  (préférer `toSignal` / `async`).
- Injection via `inject()`. Services avec `@Service()` (Angular 22).
- Nouveau control flow uniquement (`@if`, `@for` avec `track`, `@switch`).
- Inputs/outputs via `input()`, `output()`, `model()`.
- Typage strict : pas de `any`, pas de `!` non justifié, `import type` pour les types.
- Appels HTTP uniquement via le client généré (dès la Phase 1) ou un service de `core/`.
- Accessibilité : règles `template/accessibility` actives ; labels, rôles, contrastes.
- **Contenu non fiable** (réponses LLM, news) : jamais `[innerHTML]` ni `bypassSecurityTrust*`.
- Montants : formatter avec `CurrencyPipe`/`Intl`, ne pas recalculer côté front ce que le backend
  calcule.
- Sélecteurs préfixés `inv-`. Texte UI en français.
- Limites ESLint : complexité ≤ 10, fichier ≤ 300 lignes, profondeur ≤ 3, ≤ 4 paramètres.

## Tests

- Unitaires : `*.spec.ts` à côté du fichier, Vitest via `ng test`. HTTP via
  `provideHttpClientTesting()`.
- Couverture minimale : 80 % statements/lines/functions, 70 % branches (`angular.json`).
- E2E : `e2e/*.spec.ts`, Playwright ; mocker l'API avec `page.route` sauf tests d'intégration
  complète ; sélecteurs par rôle / `data-testid`.

## Commandes

```bash
npm run check        # format:check + lint + lint:styles + typecheck + test:ci + build
npm run format       # prettier --write
npm start            # dev server (proxy /api → localhost:8080)
npm run e2e
```
