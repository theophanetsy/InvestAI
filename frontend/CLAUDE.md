# Frontend — consignes

Angular 22 · TypeScript 6 strict · zoneless · Vitest · Playwright. Node 24 (`.nvmrc`).

## Structure

```
src/app/
  core/            services transverses, singletons
    api/           provideApiClient() (chemin de base du client généré)
    auth/          AuthSession (utilisateur connecté), authGuard / guestGuard, safeReturnUrl
    http/          intercepteurs d'erreurs, toProblem(), jetons HttpContext
    layout/        Shell (barre + navigation), BackendStatus, NotFound
  shared/          composants/pipes UI réutilisables, sans logique métier
  features/<nom>/  login, dashboard, chat, news, simulation — chargés en lazy via le router
  api/generated/   client OpenAPI généré (ne jamais éditer à la main)
```

## Shell, routes et erreurs HTTP (Phase 1.5)

- **UI** : Angular Material + CDK ([ADR-0008](../docs/adr/0008-bibliotheque-ui-angular-material.md)).
  Importer le composant précis (`MatButton`, `MatFormField`…), pas le module entier quand c'est
  possible. Couleurs/typo via les variables `--mat-sys-*` (thème unique dans `src/styles.scss`),
  jamais en surchargeant `.mat-mdc-*`. Pas de police ni d'icône depuis un CDN.
- **Routes** (`app.routes.ts`) : toute page en `loadComponent` (y compris le `Shell`, pour garder
  ses composants Material hors du bundle initial ; seuls le snack-bar et l'overlay CDK y restent,
  via `serverErrorInterceptor`), avec un `title` `<Titre> · InvestAI`. Une nouvelle page de
  l'app est un enfant de la route `''` (protégée par `authGuard`) et un lien dans
  `Shell.links`.
- **Inputs liés au routeur** (`withComponentInputBinding`) : tout `input()` d'un composant routé
  peut être fixé par l'URL (query param, paramètre de route) → c'est une **entrée non fiable** :
  la valider, et jamais un input qui déclenche une écriture ou vaut confirmation (un lien piégé
  `?confirm=true` ne doit rien pouvoir faire).
- **Session** : `AuthSession` (`user()`, `isAuthenticated()`, `login()`, `logout()`) ; ne jamais
  appeler `AuthService` directement depuis un composant. Seul un 401 de `me` est mémorisé comme
  « anonyme » (serveur injoignable → on redemande à la navigation suivante) ; un 401 au logout
  vaut succès. Les appels d'authentification gèrent leurs erreurs eux-mêmes (`selfHandledErrors`) :
  l'écran affiche `failureMessage(...)`.
- **Qui affiche quelle erreur** (`withApiInterceptors()`, ordre fixé dans `api-interceptors.ts`) :
  - 401 → `unauthorizedInterceptor` oublie l'utilisateur et ouvre `/login?returnUrl=<page>` ;
  - 403 `auth.csrf-invalid` → `csrfRetryInterceptor` renouvelle le cookie (GET) et rejoue une fois ;
  - statut 0 ou ≥ 500 → `serverErrorInterceptor` affiche une notification avec le `requestId` ;
  - 4xx (validation, métier) → **l'écran appelant**, qui convertit avec `toProblem(error)` et
    décide sur `problem.code`.
    L'erreur est toujours propagée : un `subscribe` de composant a **toujours** un callback
    `error` (sinon erreur non gérée dans la console). Un appel qui affiche lui-même ses erreurs se
    retire via `HttpContext` : `selfHandledErrors()` ou `REDIRECT_ON_UNAUTHORIZED` /
    `NOTIFY_SERVER_ERRORS` à `false` (ex. `BackendStatus`, appels d'authentification).
- **Redirection après connexion** : uniquement `router.navigateByUrl(safeReturnUrl(...))` (chemin
  interne, ni `//hôte`, ni `\`, ni caractère de contrôle) ; jamais `window.location`.

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
- Accessibilité : règles `template/accessibility` actives ; labels, rôles, contrastes. Focus
  clavier : contour Material renforcé pour toute l'app (`mat.strong-focus-indicators` dans
  `src/styles.scss`) ; jamais `outline: none` sans indicateur de remplacement.
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
  Côté front, `toProblem()` ajoute `network.unavailable` (statut 0, serveur injoignable) et
  `http.<statut>` pour une réponse qui n'est pas du problem+json (proxy).
- Publics sans session : `GET /api/v1/status`, `POST /api/v1/auth/login`.
- **Données de marché** (Phase 2.1, service généré `MarketService`, tag `market`, session requise).
  Tout est lu en base : ces appels ne déclenchent jamais d'appel à un fournisseur.
  - `searchInstruments(query)` → `GET /api/v1/market/instruments?query=` : ISIN exact, ticker exact
    ou fragment du nom (casse et accents ignorés), 20 résultats max, valeurs suivies d'abord.
    `query` vide ou > 100 caractères → 400 `validation.failed` (`field: "query"`).
  - `getInstrument(id)` → `GET /api/v1/market/instruments/{id}` : `InstrumentDetailsResponse`,
    `lastClose` et `trackedSince` **absents** tant qu'il n'y en a pas.
  - `getInstrumentPrices(id, from?, to?)` → `GET …/{id}/prices` : clôtures par date croissante,
    dates ISO (`2026-10-16`). Par défaut 1 an jusqu'à aujourd'hui ; 400 `validation.failed` avec
    `errors: [{ field: "from", code: "NotAfterTo" | "MaxFiveYears" }]`. Une séance sans échange n'a
    pas de clôture (trou dans la série, ne rien inventer).
  - `trackInstrument(id)` → `PUT …/{id}/tracking` : 204, idempotent (CSRF géré par `HttpClient`).
    L'historique arrive plus tard (job), `historyLoaded` passe alors à `true`.
  - Instrument inconnu → 404 `instrument.not-found`.
  - **Prix** : `close` est une **chaîne décimale** à 4 décimales (`"173.1600"`) avec `currency`
    (ISO 4217) : l'afficher avec `CurrencyPipe`/`Intl`, ne jamais la convertir en `number` pour
    calculer. `source` : `EURONEXT` (officiel) ou `EODHD` (historique, rattrapage). Clôtures
    brutes, non ajustées des dividendes ni des divisions.
  - **Activité de la séance** (Phase 2.2, `DailyCloseResponse`, aussi dans `lastClose`) :
    `volume` (titres échangés, entier) et `tradeCount` (nombre de transactions), **optionnels** :
    absents quand inconnus (clôtures antérieures à la 2.2, volume EODHD manquant, `tradeCount`
    jamais fourni par EODHD) ; ne jamais afficher 0 à la place. Définitions différentes selon
    `source` (Euronext : carnet central sur le lieu de cotation ; EODHD : non documentée) : ne pas
    comparer ni additionner des volumes de sources différentes.
  - Toutes les valeurs du référentiel de Paris ont des clôtures `EURONEXT` depuis leur collecte
    (suivies ou non) ; seules les valeurs suivies ont 1 an d'historique et le rattrapage des jours
    manqués : une valeur non suivie peut avoir des **trous** (jours où l'application était
    éteinte). Ne jamais interpoler.

## Pièges connus

Format : symptôme → cause → bonne pratique. Compléter à chaque étape (workflow, étape 7).

- **Client généré qui appelle `http://localhost/…` ou `//api/…`** : chemin de base par défaut
  du générateur → toujours `provideApiClient()` (`core/api`), dans `app.config.ts` **et** dans
  les tests qui utilisent un service généré.
- **Variable ou membre `private` inutilisé** : vérifié par ESLint (`no-unused-vars`,
  `no-unused-private-class-members` de typescript-eslint), plus par
  `tsc` (désactivé pour tout le projet à cause du code généré, ADR-0004) → `npm run lint` avant
  de conclure. Comme avec `tsc`, un paramètre imposé par une signature mais inutile se préfixe
  `_` (ex. `(_route, state) =>` dans un garde).
- **`@typescript-eslint/unbound-method` sur `Validators.required`** : méthode statique passée
  sans son objet → pour un champ obligatoire, l'attribut `required` du template suffit
  (`RequiredValidator` s'applique aussi à `formControlName`, et pose `aria-required`).
- **`no-deprecated` sur `HttpErrorResponse.statusText`** : faux en HTTP/2 → ne jamais l'afficher,
  passer par `toProblem()`.
- **`inject-at-top`** : tous les `inject()` en champs `private readonly` en tête de classe, avant
  `input()` et les champs qui les utilisent.
- **`component-max-inline-declarations`** : template inline ≤ 3 lignes, sinon `templateUrl`.
- **Budget `initial` (500 kB) dépassé à l'ajout de composants Material** : un composant chargé
  par une route `component:` est dans le bundle initial → `loadComponent` partout ; ne jamais
  relever le budget.
- **Test de routes : `expectOne('/api/v1/auth/me')` ne trouve rien** : les gardes s'exécutent
  après le démarrage asynchrone de la navigation → `await vi.waitFor(() => http.expectOne(…))`
  (cf. `app.spec.ts`).
- **E2E : formulaire Material** : `page.getByLabel('Identifiant')` fonctionne (le `mat-label`
  est relié à l'input). **Notification (snack-bar) introuvable par `getByRole('status')`** :
  le texte n'est pas dans l'élément de rôle `status` → la chercher par son texte (`getByText`).
- **Requête rejouée dans un intercepteur avec l'ancien jeton CSRF** : `provideHttpClient` place
  l'intercepteur XSRF d'Angular avant ceux de `withInterceptors` et il n'écrase jamais un
  en-tête existant → relire `HttpXsrfTokenExtractor.getToken()` et poser `X-XSRF-TOKEN`
  soi-même (cf. `csrf-retry.interceptor.ts`). Le logout supprime le cookie `XSRF-TOKEN` : le
  POST de connexion suivant part sans jeton et passe par ce rejeu.
- **Page plein écran qui défile de quelques pixels** : `min-height: 100dvh` + `padding` sans
  `box-sizing: border-box` (le padding s'ajoute) → toujours `border-box` sur ce type de conteneur ;
  test e2e `expectNoScroll` (`e2e/support/layout.ts`).
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
  complète ; sélecteurs par rôle / `data-testid`. Aides de mise en page dans
  `e2e/support/layout.ts` : `tabTo` (focus au clavier), `expectFocusRing`, `expectNoScroll`.

## Commandes

```bash
npm run check        # format:check + lint + lint:styles + typecheck + test:ci + build
npm run format       # prettier --write
npm start            # dev server (proxy /api → localhost:8080)
npm run e2e
```
