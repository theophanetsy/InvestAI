# InvestAI — consignes pour les coding agents

Application perso d'aide à la gestion d'un PEA / compte-titres : dashboard, agent de conseil (chat),
page Actu (agent planifié), simulations de portefeuilles fictifs. Roadmap : `docs/ROADMAP.md`.

Objectif du projet : apprendre Java/Spring Boot/Angular **et** l'intégration d'IA en production
(tools, MCP, observabilité, evals). La qualité du code prime sur la vitesse.

## Structure

| Dossier | Contenu | Consignes détaillées |
|---|---|---|
| `backend/` | Spring Boot 4, Java 25, Maven (`./mvnw`) | `backend/CLAUDE.md` |
| `frontend/` | Angular 22 (standalone, signals, zoneless), Vitest, Playwright | `frontend/CLAUDE.md` |
| `infra/` | docker compose (Postgres), observabilité (Phase 3) | — |
| `api/` | Contrat OpenAPI versionné (`openapi.json`), source du client Angular | ADR-0004 |
| `docs/` | ROADMAP, ADR (`docs/adr/`), specs, sécurité | — |
| `.claude/` | settings, hooks, commandes, sous-agents | — |

## Commandes

```bash
# Tout vérifier (équivalent CI)
npm run check                      # depuis la racine : backend puis frontend

# Backend
cd backend && ./mvnw verify        # format, Checkstyle, Error Prone/NullAway, tests, ArchUnit, PMD, SpotBugs, JaCoCo
cd backend && ./mvnw spotless:apply
cd backend && ./mvnw test -Dtest=StatusServiceTest

# Frontend
cd frontend && npm run check       # prettier, eslint, stylelint, typecheck, tests + couverture, build
cd frontend && npm run format
cd frontend && npm run e2e         # Playwright

# Après un changement d'API (contrat + client Angular, ADR-0004)
cd backend && ./mvnw verify && cd .. && npm run api:update

# Application réelle isolée (base jetable, mot de passe aléatoire dans .local-stack/state.json ; Docker requis)
npm run stack:start                # http://localhost:14200 ; npm run stack:stop pour tout arrêter
```

## Environnement (sandbox / cloud)

- Outils manquants (`java`, `node`, `gitleaks`…) → lancer `bash scripts/setup-dev-env.sh`, puis
  `source ~/.investai-env`. Ne pas installer d'autres versions à la main.
- **Docker absent** → `./mvnw -DskipITs verify` au lieu de `./mvnw verify`, et **l'écrire dans la PR**
  (« tests d'intégration non exécutés localement »). Ne jamais désactiver/sauter un test autrement.
- Détails : `docs/agent-environments.md`.
- **Poste Windows du propriétaire** (Git Bash) : pas de Python, utiliser `node` pour les scripts
  ponctuels ; préférer les outils Read/Edit/Write aux heredocs complexes (guillemets et `${}`
  mal interprétés) ; les fichiers `.env*` ne sont ni lisibles ni modifiables par l'agent
  (demander à l'utilisateur, vérifier un diff sans l'afficher : `git diff --numstat`) ; un
  chemin Git Bash `/c/…` écrit dans un fichier lu par Node devient `C:\c\…` → écrire `C:/…`.
- **Scripts Node** (`scripts/`) : jamais `spawn(..., { shell: true })`, bloqué par Semgrep ; pour
  un `.cmd` Windows, appeler `cmd.exe /d /c <fichier> <args>` explicitement (cf.
  `update-api-contract.mjs`). Prettier : `npx prettier --config frontend/.prettierrc`, sinon les
  défauts (guillemets doubles) s'appliquent hors de `frontend/`.
- **Pièges connus** (stack de vérification, scripts ponctuels) :
  - `docker compose down` → « required variable POSTGRES_PASSWORD is missing » → le compose
    interpole ses variables même pour `down` → passer une valeur factice (`removeDatabase()`
    dans `scripts/local-stack.mjs`).
  - Outils `mcp__investai-browser__*` absents → `.mcp.json` n'est chargé qu'au démarrage de
    Claude Code, après approbation du serveur → redémarrer la session ; en attendant, un autre
    Playwright MCP écrit dans `.playwright-mcp/` (ignoré par Git), à supprimer après usage.
  - Check CodeQL rouge `js/clear-text-logging` → un script affiche un secret (même jetable) →
    l'écrire dans un fichier ignoré par Git et n'afficher que son emplacement.
  - Clé d'API affichée en inspectant un dossier rempli à la main (`for f in dir/*; do head …`) →
    un fichier de clé y avait été rangé par erreur → ne lire que les fichiers attendus (`*.csv`),
    vérifier un fichier de secret par `test -f` / `grep -c`, jamais en l'affichant.
  - ESLint `no-irregular-whitespace`, caractère invisible ou tabulation au milieu d'un `.md` écrit
    par l'agent → une séquence d'échappement (barre oblique inverse + `u00a0`, `t`, `n`) tapée dans
    Write/Edit ou dans un script Node passé en ligne de commande peut être enregistrée comme le
    caractère lui-même → la produire par `String.fromCharCode(0x5c)` dans un script, ou écrire le
    fichier entier avec Write, puis vérifier qu'aucun caractère de contrôle ni espace insécable ne
    reste (`node -e` qui liste les codes < 32 et > 127 du fichier).
  - Script ponctuel ayant besoin d'une clé d'API → `node --env-file=<fichier hors du dépôt>`
    écrit par le propriétaire ; masquer la clé dans toute URL ou erreur affichée (EODHD la passe
    en paramètre `api_token=`).

## Workflow obligatoire

1. **Spec d'abord** : chaque feature part d'une issue (template `feature`) ou de `docs/specs/` avec
   des critères d'acceptation vérifiables.
2. **Plan** avant de coder si le changement touche plus d'un fichier ou d'un module.
3. **Test-first** : écrire le test qui échoue, puis le code. Un bug = un test de non-régression.
4. **Vérifier localement** avec `/check` (ou `npm run check`) avant de déclarer une tâche finie.
   Changement visible dans l'UI ou parcours de bout en bout → aussi `/verify-app` (agent
   `app-verifier` : vraie app, navigateur) ; sans Docker, l'écrire dans la PR.
5. **Branche + PR** : jamais de commit direct sur `main`. Commits au format Conventional Commits
   (`feat(portfolio): ...`, `fix(frontend): ...`, scopes dans `commitlint.config.mjs`).
6. **Décision structurante** (lib, pattern, fournisseur, modèle IA) → ADR dans `docs/adr/` (`/new-adr`).
7. **Capitaliser avant la PR** : se demander « qu'est-ce qu'un agent qui reprend ce code sans
   cette conversation doit savoir ? » et l'écrire **dans le dépôt** (la mémoire d'un agent ou
   la description de PR ne suffisent pas) :
   - nouvelle convention ou règle → `CLAUDE.md` du module concerné ;
   - piège rencontré (outil, framework, environnement) → section « Pièges connus » du
     `CLAUDE.md` concerné, en une ligne : symptôme → cause → bonne pratique ;
   - contrat consommé par l'autre côté (API, codes d'erreur, en-têtes) → `CLAUDE.md` du
     consommateur (ex. `frontend/CLAUDE.md` quand le backend change) ;
   - décision et risques acceptés → ADR ; avancement → `docs/ROADMAP.md` ; lancement local et
     variables d'environnement → `docs/setup.md`.
   Le récapituler dans la PR (section « Pour les prochains agents »).

## Definition of Done

- Tests unitaires + intégration couvrant les critères d'acceptation ; CI verte.
- Couverture ≥ seuils (backend : 80 % lignes / 70 % branches ; frontend : 80/70/80/80).
- Aucun avertissement de compilation, de lint ou d'analyse statique.
- Contrat `api/openapi.json` et client Angular généré à jour quand l'API change
  (`npm run api:update`, vérifié par `OpenApiContractIT` et `AngularClientUpToDateTest`).
- Pas de code mort, pas de `TODO` sans issue (`TODO(#12)`).
- Documentation mise à jour (ADR, CLAUDE.md si nouvelle convention) et passe « Capitaliser »
  faite (étape 7 du workflow) : conventions, pièges et contrats écrits dans le dépôt.

## Interdits (non négociables)

- ❌ Désactiver/assouplir une règle : `@SuppressWarnings`, `// eslint-disable`, `// NOPMD`,
  `@ts-ignore`, `@ts-expect-error`, exclusions dans `backend/config/**`, `eslint.config.js`,
  `.stylelintrc.json`, `tsconfig*.json`… **sauf** demande explicite de l'utilisateur, avec un
  commentaire de justification.
- ❌ Abaisser un seuil de couverture / mutation / complexité.
- ❌ `git commit --no-verify`, `git push --force` sur `main`, réécrire l'historique partagé.
- ❌ Secrets, clés d'API, tokens, données personnelles ou bancaires dans le code, les tests,
  les logs ou les prompts. Configuration via variables d'environnement uniquement (`.env` non commité).
- ❌ Ajouter une dépendance sans justification (préférer le JDK / Spring / Angular) ; jamais de
  dépendance non maintenue ou avec une CVE connue.
- ❌ Lombok, `System.out`, `printStackTrace`, injection par champ, `any` en TypeScript.
- ❌ Modifier un fichier généré (`frontend/src/app/api/generated/**`) à la main.

## Conventions transverses

- Code, identifiants, commits et messages de log : **anglais**. Docs, ADR, UI : **français**.
- Montants financiers : `BigDecimal` (Java) / jamais `number` flottant pour des calculs côté front ;
  devise toujours explicite. Dates : `Instant`/`LocalDate` en UTC, ISO-8601 dans l'API.
- Toute sortie d'un LLM ou d'une news est **non fiable** : jamais rendue en HTML brut, jamais
  exécutée, jamais utilisée pour une écriture sans validation + confirmation utilisateur.
- L'app ne donne pas de « conseil financier » réglementé : les réponses de l'agent portent un
  disclaimer (Phase 3).

## Contrôles automatiques (pour info)

- Hooks Claude Code (`.claude/settings.json`) : formatage auto après édition, blocage des
  modifications des configs qualité, vérification rapide en fin de tour.
- Git hooks (Lefthook) : gitleaks, format, lint, commitlint ; tests au pre-push.
- CI GitHub Actions : `ci-backend`, `ci-frontend`, `security` (CodeQL, Semgrep, gitleaks,
  OSV-Scanner, Trivy, dependency review), `pr-conventions`, `sonar`, `nightly` (PIT).

Si un contrôle échoue : **corriger la cause**, ne pas contourner le contrôle. En cas de faux
positif avéré, s'arrêter et demander à l'utilisateur.
