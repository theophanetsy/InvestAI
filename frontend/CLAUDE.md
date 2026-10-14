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
- Appels HTTP uniquement via le client généré (`src/app/api/generated`, services par module :
  `inject(SystemService).getStatus()`) ou un service de `core/`. Jamais de type ou d'URL d'API
  écrits à la main.
- Accessibilité : règles `template/accessibility` actives ; labels, rôles, contrastes.
- **Contenu non fiable** (réponses LLM, news) : jamais `[innerHTML]` ni `bypassSecurityTrust*`.
- Montants : formatter avec `CurrencyPipe`/`Intl`, ne pas recalculer côté front ce que le backend
  calcule.
- Sélecteurs préfixés `inv-`. Texte UI en français.
- Limites ESLint : complexité ≤ 10, fichier ≤ 300 lignes, profondeur ≤ 3, ≤ 4 paramètres.

## Contrat de l'API backend (ADR 0006, ADR 0007)

- **Même origine** : appels en chemins relatifs `/api/v1/…` (proxy `/api` → `:8080` en dev),
  jamais d'URL absolue ni de CORS.
- **Session** : cookie `HttpOnly` géré par le navigateur, invisible pour le code. État connecté :
  `GET /api/v1/auth/me` → 200 `{ username }` ou 401. Connexion :
  `POST /api/v1/auth/login` `{ username, password }` → 204 ; échec → 401
  `auth.invalid-credentials`. Déconnexion : `POST /api/v1/auth/logout` → 204.
- **CSRF** : le backend pose le cookie `XSRF-TOKEN` ; `HttpClient` le renvoie seul dans
  l'en-tête `X-XSRF-TOKEN` (`withXsrfConfiguration` par défaut). Le jeton change à la connexion :
  ne jamais le mettre en cache. Jamais en paramètre d'URL ou de formulaire (refusé).
- **Erreurs** : RFC 9457 `application/problem+json` avec `status`, `title`, `code` stable,
  `requestId`, parfois `errors: [{ field, code }]` ; jamais de `detail`. Décider sur `code`
  (pas sur `title`), afficher `requestId` pour le support. Codes : `auth.required` (401 →
  écran de connexion), `auth.invalid-credentials`, `auth.forbidden`, `auth.csrf-invalid` (403 →
  recharger le jeton : refaire un GET), `validation.failed` (400, erreurs par champ),
  `http.<statut>` (erreurs génériques), `internal.error` (500), codes métier `<module>.<cas>`.
  Type TypeScript : le modèle généré `Problem` (`api/generated`), jamais une interface à la main.
- Publics sans session : `GET /api/v1/status`, `POST /api/v1/auth/login`.

## Pièges connus

Format : symptôme → cause → bonne pratique. Compléter à chaque étape (workflow, étape 7).

- **Client généré qui appelle `http://localhost/…` ou `//api/…`** : chemin de base par défaut
  du générateur → toujours `provideApiClient()` (`core/api`), dans `app.config.ts` **et** dans
  les tests qui utilisent un service généré.
- **Variable ou membre `private` inutilisé** : vérifié par ESLint (`no-unused-vars`,
  `no-unused-private-class-members` de typescript-eslint), plus par
  `tsc` (désactivé pour tout le projet à cause du code généré, ADR-0004) → `npm run lint` avant
  de conclure. `args: 'all'` sans exception : un paramètre préfixé `_` est aussi refusé.
- **Modifier `api/generated`** : interdit, le build backend le compare au contrat →
  `npm run api:update` depuis la racine.
- **`eslint --max-warnings=0 <fichiers>` échoue sur « File ignored because of a matching ignore
  pattern »** : un fichier ignoré (ex. `api/generated`) passé explicitement produit un
  avertissement → toujours ajouter `--no-warn-ignored` quand on lint une liste de fichiers
  (hook Stop, Lefthook).

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
