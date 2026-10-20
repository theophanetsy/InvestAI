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
    format/        decimal.ts : montants en chaînes décimales, format français (jamais de float)
    pea-eligibility/, confirm-dialog/
  features/<nom>/  login, dashboard, profile, portfolio, chat, news, simulation — lazy (router)
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
- Montants : chaînes décimales de l'API formatées par `shared/format/decimal.ts`
  (`formatAmount`, `formatPercent`…, jamais via un `number`), devise lue dans les données ; ne pas
  recalculer côté front ce que le backend calcule.
- **Erreur affichée sur place** (alerte à côté du formulaire, message d'une liste) : appeler le
  service généré avec `{ context: inlineErrors() }` (`core/http/http-context.ts`), sinon une
  erreur 5xx ou réseau est signalée deux fois (notification + alerte).
- **Annonces** : un message de confirmation est un `role="status"` (ou `aria-live`) **toujours
  présent** dans le DOM, dont seul le texte change ; une action réussie sans changement visible
  évident (ajout sous le tableau) s'annonce par une notification (`MatSnackBar`).
- **Formulaire réinitialisé après succès** : `FormGroupDirective.resetForm()` (`viewChild`), pas
  `FormGroup.reset()` qui laisse `submitted` et donc les erreurs Material affichées.
- **Tableau Material** : `*matCellDef="let row"` n'est pas typé → construire les lignes affichées
  en TypeScript (modèle de vue, cf. `portfolio-table.ts`) et `[trackBy]` sur l'identifiant (sinon
  les lignes sont recréées à chaque rechargement et le focus rendu par un dialogue est perdu).
- **Dialogue qui enregistre** : il appelle l'API lui-même, `disableClose` pendant l'envoi, efface
  l'erreur précédente, se ferme avec `true` ; la page recharge et annonce.
- **Graphiques** : SVG dessiné dans un composant (`features/dashboard/performance-chart.ts`),
  géométrie en fonctions pures testées (`chart-geometry.ts`) ; **aucune bibliothèque de
  graphiques** sans ADR. Obligatoire : `role="img"` + `aria-labelledby` (titre) +
  `aria-describedby` (phrase de synthèse visible), tableau des données en alternative
  (`chart-data-table`, dans un `<details>`), séries distinguées par le motif du trait en plus de la
  couleur, couleurs `--mat-sys-*` (clair / sombre automatiques). Seule exception à « jamais de
  `number` » : convertir une valeur pour placer un point (`toNumbers`), jamais pour un chiffre
  affiché.
- **Tableau large** : dans une zone `overflow: auto` atteignable au clavier (`tabindex="0"`,
  `role="region"`, `aria-label`), titre **hors** de la zone (`<p id>` + `aria-labelledby` sur la
  table) pour qu'il reste lisible sur téléphone.
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
    (ISO 4217) : l'afficher avec `formatAmount` (`shared/format/decimal.ts`), ne jamais la
    convertir en `number`. `source` : `EURONEXT` (officiel) ou `EODHD` (historique,
    rattrapage). Clôtures brutes, non ajustées des dividendes ni des divisions.
  - **Activité de la séance** (Phase 2.2, `DailyCloseResponse`, aussi dans `lastClose`) :
    `volume` (titres échangés, entier) et `tradeCount` (nombre de transactions), **optionnels** :
    absents quand inconnus (clôtures antérieures à la 2.2, volume EODHD manquant, `tradeCount`
    jamais fourni par EODHD) ; ne jamais afficher 0 à la place. Définitions différentes selon
    `source` (Euronext : carnet central sur le lieu de cotation ; EODHD : non documentée) : ne pas
    comparer ni additionner des volumes de sources différentes.
  - **Éligibilité PEA** (Phase 2.3) : `peaEligibility: { status, source }` dans
    `InstrumentSummaryResponse` et `InstrumentDetailsResponse`. `status` : `ELIGIBLE`,
    `NOT_ELIGIBLE`, `UNKNOWN` ; `source` : `RULE` (règle UE/EEE, à afficher « probable : vérifier
    chez le courtier ») ou `OWNER` (correction du propriétaire). Badge :
    `shared/pea-eligibility` (texte, jamais la couleur seule).
    `correctPeaEligibility(id, { status })` → `PUT …/{id}/pea-eligibility` : 200
    `PeaEligibilityResponse` (source `OWNER`), jamais écrasé ensuite par la règle.
  - Toutes les valeurs du référentiel de Paris ont des clôtures `EURONEXT` depuis leur collecte
    (suivies ou non) ; seules les valeurs suivies ont 1 an d'historique et le rattrapage des jours
    manqués : une valeur non suivie peut avoir des **trous** (jours où l'application était
    éteinte). Ne jamais interpoler.
- **Profil et portefeuille** (Phase 2.3, service généré `PortfolioService`, tag `portfolio`) :
  - `getProfile()` → `GET /api/v1/profile` : `ProfileResponse` ; **404 `profile.not-found`** tant
    qu'il n'est pas rempli (état « vide », pas une erreur). `saveProfile(ProfileRequest)` → `PUT`,
    idempotent, renvoie le profil. `listRiskProfiles()` → `[{ code, label, description }]` :
    libellés et descriptions à afficher tels quels (même texte que pour l'agent).
  - `objectives` : texte libre **non fiable** (≤ 500), à afficher par interpolation uniquement.
  - `getPortfolio()` → `PortfolioResponse` : `lines` triées par nom, `total` (espèces comprises),
    `cash?`, `complete` / `unpricedLines`. Une ligne sans clôture n'a ni `lastClose`, ni `value`,
    ni `weightPercent`, ni plus-value : afficher « Cours indisponible », jamais 0.
    `unrealizedGain*` absents sans prix moyen. `instrument` est un `InstrumentSummaryResponse`.
  - `addPortfolioLine({ instrumentId, quantity, averagePrice? })` → 201 ; 422
    `portfolio.line.duplicate-instrument` ou `portfolio.line.currency-not-supported`, 404
    `instrument.not-found`. `updatePortfolioLine(id, { quantity, averagePrice? })` (prix absent =
    effacé), `deletePortfolioLine(id)` → 204 ; 404 `portfolio.line.not-found`.
    `setPortfolioCash({ amount })` → 200.
  - **Montants envoyés en chaînes décimales** (`"20000.50"`, point décimal, ≤ 2 décimales ; prix
    ≤ 4), quantité entière (un `1.5` est un 400). Saisie française → `normalizeDecimal`
    (`shared/format/decimal.ts`) ; affichage → `formatAmount`/`formatPercent`/`formatDate`.
    Jamais `Number(...)`/`parseFloat` sur un montant.
- **Indicateurs** (Phase 2.4, [spec](../docs/specs/phase-2-04-indicators-dashboard.md)) :
  - Tout indicateur est un `IndicatorResponse { status, value? }` : `AVAILABLE` (`value` = chaîne
    décimale), `INSUFFICIENT_HISTORY` (moins de 80 % des séances de la fenêtre) ou
    `NOT_COMPUTABLE` (indéfini). Afficher avec `formatIndicator(indicator, kind)`
    (`shared/format/indicator.ts` : « Historique insuffisant », « Non calculable »), jamais 0 à la
    place. Pourcentages et points à 2 décimales, Herfindahl à 4, corrélation et bêta à 2.
  - `getPortfolioIndicators()` → `GET /api/v1/portfolio/indicators` (service `PortfolioService`,
    calcul à la demande) : `asOf?` (date des cours, absente sans aucun cours), `total`,
    `composition` (`lineCount`, plus grosse ligne, Herfindahl, parts espèces / ETF / actions /
    autres), `volatility` et `maxDrawdown` du **portefeuille actuel comme s'il avait été détenu
    depuis un an, espèces exclues**, `lines` (triées par nom : `weightPercent?`, `indicators?` de la
    valeur, `exclusion?` = `NOT_VALUED` | `INSUFFICIENT_HISTORY` quand la ligne est hors du
    risque), `curve` (une entrée par séance, base 100, `portfolio?` / `msciWorld?` / `cac40?`
    absents sur un trou : ne jamais relier ni interpoler ; vide si l'historique est insuffisant),
    `correlations` (`instrumentIds` + `rows`).
  - `IndicatorsResponse` (par valeur) : `asOf`, `closesOneYear`, `performanceOneMonth` …
    `performanceOneYear` (hors dividendes), `volatility`, `maxDrawdown`, `msciWorld` / `cac40`
    (`outperformance`, `correlation`, `beta`), `averageDailyTradedAmount` (+ `currency`).
  - `MarketService.getInstrumentIndicators(id)` (404 `indicators.not-computed` avant le premier
    calcul) et `screenInstruments(...)` (filtre de tout Paris, 50 résultats au plus) existent pour
    l'agent ; pas encore d'écran.
  - **Limite à toujours afficher** : clôtures brutes, pas d'ajustement des divisions ni des
    attributions gratuites (`RAW_CLOSES_LIMIT` dans `dashboard-warnings.ts`).
- **Enums générés** : un enum TypeScript par DTO (`RiskProfileResponseCodeEnum`,
  `ProfileRequestRiskProfileEnum`…) pour les mêmes valeurs, non assignables entre eux → garder la
  valeur en `string` dans le formulaire et la reconvertir avant l'envoi (`toRequestRiskProfile`).

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
- **`template/cyclomatic-complexity` (> 5)** : compte chaque `@if`/`@else if`/`@else`/`@for` du
  template entier → découper en sous-composants (`portfolio-table`, `portfolio-summary`,
  `risk-profile-field`…), calculer les textes dans la classe (`gainText`, `searchHint`) et ne
  pas entourer un `<mat-error>` d'un `@if` : Material ne l'affiche qu'en erreur.
- **`template/attributes-order`** : directive structurelle d'abord
  (`<td *matCellDef="let line" mat-cell>`) ; **`prefer-template-literal`** : littéral de gabarit
  (backquotes) dans `[attr.aria-label]` plutôt qu'une concaténation `'Supprimer ' + name`.
- **`(ngSubmit)` jamais appelé** : un `<form>` sans `[formGroup]` (contrôle seul en
  `[formControl]`) n'a pas de directive de formulaire → toujours un `FormGroup` (`cash-form`).
- **Test d'un texte formaté** : `/\s+/` capture aussi les espaces insécables des montants
  (U+00A0 avant `€` et `%`, U+202F entre milliers) → normaliser avec `/[ \n]+/`.
- **`Intl.NumberFormat.format("1234.56")`** refusé par TypeScript (surcharge chaîne : lib ES2023,
  absente) → formater le texte soi-même (`formatDecimal`), sans passer par un `number`.
- **`[maxlength]` sur un `<input>` sans contrôle de formulaire** : NG8002 → attribut statique
  `maxlength="100"`.
- **E2E : `getByLabel` sur un champ à autocomplétion** trouve aussi le `listbox` ouvert (même
  libellé) → `getByRole('combobox', { name })`.
- **Test d'un composant enfant** : `debugElement.query(By.directive(X)).componentInstance` est
  `any` (ESLint `no-unsafe-*`) → `.injector.get(X)`.
- **`aria-invalid`** : Material retire l'attribut quand le champ est valide (`null`, pas `"false"`).
- **`eslint --max-warnings=0 <fichiers>` échoue sur « File ignored because of a matching ignore
  pattern »** : un fichier ignoré (ex. `api/generated`) passé explicitement produit un
  avertissement → toujours ajouter `--no-warn-ignored` quand on lint une liste de fichiers
  (hook Stop, Lefthook).

- **Texte collé à l'élément précédent** (« 0,00 €(espèces comprises) ») : Angular supprime le blanc
  seul entre deux éléments (`preserveWhitespaces: false`), et `&ngsp;` n'y a rien changé → mettre
  l'espace dans l'élément (`<span>&nbsp;(…)</span>`), test de non-régression sur le texte.
- **`Array.prototype.findLast` / `toReversed` refusés par `tsc`** : lib ES2023 absente (tsconfig
  protégé) → `[...values].reverse().find(...)`.
- **Deux séries de même couleur dans le thème sombre** : avec une seule palette
  (`mat.$azure-palette`), `--mat-sys-tertiary` vaut `--mat-sys-primary` → `--mat-sys-secondary`
  ou `--mat-sys-on-surface-variant`, et toujours un motif de trait différent (test e2e en thème
  clair et sombre, `page.emulateMedia({ colorScheme })`).
- **Test de routes ou e2e en échec après l'ajout d'un appel au tableau de bord** : la page d'accueil
  charge `GET /api/v1/portfolio/indicators` → `app.spec.ts` y répond (`EMPTY_INDICATORS`) et
  `mockApi` (e2e) le simule par défaut ; un test le redéfinit avec `mockDashboardApi` (la dernière
  route enregistrée gagne).
- **Espace insécable littéral dans un spec** (constante `NBSP` qui contient U+00A0 au lieu de
  l'échappement) : `\u00a0` tapé dans Write/Edit est enregistré comme le caractère lui-même
  → vérifier les fichiers écrits (script qui
  liste les codes `a0`, `202f`) et réécrire l'échappement avec `String.fromCharCode(0x5c)`.

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
