# Plan de développement — InvestAI

## Contexte

InvestAI est un projet perso à double objectif :
1. **Apprendre** Java / Spring Boot / Angular (compétences manquantes) et le développement assisté par coding agents.
2. **Intégrer de l'IA dans une web app** de façon "production-grade" : agent conversationnel, tools, MCP, modèles frontier, observabilité LLM, évaluations, tests.

Fonctionnel : suivi PEA + compte-titres (dashboard), agent de conseil toujours disponible (selon horizon court/moyen/long terme), page **Actu** alimentée par un agent planifié qui analyse les news boursières, page **Simulation** (portefeuilles fictifs, achats datés, suivi de performance) — simulations créables par l'agent depuis le chat, pour mesurer la pertinence de ses conseils.

État actuel : repo vide (`README.md` seul, 1 commit sur `main`).

Décisions prises :
- **Stack** : Java (LTS récente) + Spring Boot, Angular (standalone, signals), PostgreSQL, monorepo.
- **IA** : LangChain4j (+ starter Spring Boot). Choix des modèles/fournisseurs reporté à la phase Agent.
- **Hébergement** : local via Docker Compose d'abord, cloud en dernière phase.
- **Repo/CI** : GitHub + GitHub Actions.
- Intégration bancaire et choix de modèles : décidés au moment de l'étape concernée.

Principe directeur : **chaque phase se termine par une app qui tourne, CI verte, quality gate passé**. Aucune ligne de code métier avant que la Phase 0 soit en place.

---

## Phase 0 — Garde-fous qualité & consignes pour coding agents (AVANT tout code métier)

Objectif : un repo où il est *impossible* de merger du code non formaté, non testé, vulnérable ou hors conventions — que l'auteur soit humain ou agent.

Un "walking skeleton" minimal (1 endpoint `/health` + 1 composant Angular) est créé uniquement pour que les outils aient du code sur lequel s'exécuter et pour prouver que chaque contrôle **échoue** quand on introduit une violation.

### 0.1 Structure du monorepo
```
/backend        Spring Boot (Maven, wrapper mvnw)
/frontend       Angular (npm, Node LTS épinglé via .nvmrc)
/infra          docker-compose, configs observabilité
/docs           ADR, architecture, glossaire métier
/.github        workflows, templates PR/issues, CODEOWNERS, dependabot
/.claude        settings, hooks, commands, skills, agents
CLAUDE.md / AGENTS.md
```

### 0.2 Consignes pour les coding agents
- **`CLAUDE.md` racine** (+ `AGENTS.md` en lien pour les autres agents) : vision, architecture, commandes (build/test/lint), conventions, Definition of Done, interdits (pas de secrets, pas de `@SuppressWarnings` sans justification, pas de désactivation de règle de lint, pas de `--no-verify`, pas de modification des seuils de couverture).
- **`backend/CLAUDE.md`** et **`frontend/CLAUDE.md`** : conventions spécifiques (couches, nommage, gestion d'erreurs, DTO vs entités, patterns Angular signals/OnPush, etc.).
- **Definition of Done** explicite : tests unitaires + intégration, lint OK, couverture ≥ seuil, OpenAPI à jour, ADR si décision structurante, CHANGELOG/commit conventionnel.
- **`.claude/settings.json`** : permissions (deny lecture `.env*`, `**/secrets/**`), hooks :
  - `PostToolUse` sur Edit/Write → format auto (Spotless / Prettier) du fichier modifié.
  - `PreToolUse` → bloquer l'édition des fichiers de config qualité (seuils, règles) sauf demande explicite.
  - `Stop` → lancer le lint/test rapide du module touché.
- **Slash commands / skills projet** : `/new-feature` (workflow spec → tests → impl → review), `/new-adr`, `/check` (lance toute la chaîne qualité locale), `/security-review`.
- **Sub-agents** dédiés : reviewer Java, reviewer Angular, reviewer sécurité.
- **`docs/adr/`** : ADR-0001 stack, ADR-0002 LangChain4j, ADR-0003 conventions d'API (contract-first OpenAPI).

### 0.3 Contrôles backend (Maven, exécutés en `verify`)
| Domaine | Outil |
|---|---|
| Formatage | Spotless (google-java-format / palantir) |
| Style | Checkstyle |
| Bugs / code smells | PMD, SpotBugs |
| Sécurité code | FindSecBugs (plugin SpotBugs) |
| Nullabilité / erreurs compile | Error Prone + NullAway |
| Architecture | ArchUnit (règles de couches, pas de cycle, domaine indépendant de Spring) |
| Couverture | JaCoCo avec seuil bloquant (ex. 80 % lignes / 70 % branches) |
| Qualité des tests | PIT mutation testing (sur le domaine, nightly) |
| Dépendances | Maven Enforcer (versions, Java), OWASP Dependency-Check ou OSV-Scanner |
| Tests d'intégration | Testcontainers (Postgres réel) |

### 0.4 Contrôles frontend
| Domaine | Outil |
|---|---|
| Lint | ESLint + angular-eslint (strict), règles d'accessibilité template |
| Formatage | Prettier, Stylelint |
| Typage | TypeScript `strict` + `strictTemplates` |
| Tests unitaires | Vitest (ou Jest) avec seuil de couverture |
| E2E | Playwright |
| Dépendances | `npm audit` / OSV-Scanner |
| Client API | généré depuis l'OpenAPI (openapi-generator) — jamais écrit à la main |

### 0.5 Contrôles transverses
- **Pre-commit** (Lefthook) : format, lint des fichiers stagés, gitleaks, commitlint (Conventional Commits).
- **Secrets** : gitleaks (local + CI) + GitHub secret scanning / push protection.
- **SAST** : CodeQL (Java + TS) + Semgrep (règles OWASP, Spring, Angular).
- **SCA** : Dependabot (Maven, npm, Actions, Docker) + OSV-Scanner.
- **Conteneurs** : hadolint (Dockerfiles), Trivy (images + IaC).
- **Quality gate** : SonarCloud (gratuit), gate bloquant sur nouveau code.
- **GitHub** : protection de `main` (PR obligatoire, checks requis, historique linéaire), PR template avec checklist DoD, CODEOWNERS, `.editorconfig`.
- **Workflows Actions** : `ci-backend.yml`, `ci-frontend.yml`, `security.yml` (CodeQL/Semgrep/Trivy/gitleaks), `nightly.yml` (PIT, scans complets). Path filters pour ne lancer que le nécessaire.

**Sortie de phase** : PR de démonstration volontairement fautive (format, faille, test manquant, secret factice) → tous les contrôles la bloquent.

---

## Phase 1 — Squelette technique & socle

- Backend Spring Boot : Web, Validation, Data JPA, Flyway, Actuator, Security, springdoc-openapi. Architecture hexagonale légère par module métier (`portfolio`, `market`, `assistant`, `news`, `simulation`) : `domain` / `application` / `infrastructure` / `api`.
- Gestion d'erreurs uniforme (RFC 9457 Problem Details), validation, logs JSON structurés.
- **Auth** : mono-utilisateur pour l'instant, mais sécurisée (Spring Security, session ou JWT ; prévoir OIDC pour le cloud).
- Frontend Angular : shell (layout, routing lazy par feature : dashboard / chat / actu / simulation), lib UI (Angular Material ou PrimeNG — à trancher via ADR), client API généré, gestion d'erreurs HTTP.
- `infra/docker-compose.yml` : Postgres, backend, frontend, + stack observabilité.
- **Observabilité socle** : Micrometer + OpenTelemetry → Grafana LGTM (Loki, Tempo, Prometheus/Mimir) en un conteneur. Traces corrélées front → back → DB.
- Configuration par profils (`local`, `test`, `prod`), secrets via variables d'environnement / `.env` non commité.

---

## Phase 2 — Portefeuilles réels & Dashboard

- **Modèle de domaine** : `Account` (type PEA / CTO, plafond de versements PEA), `Instrument` (ISIN, ticker, place, devise, éligibilité PEA), `Transaction` (achat, vente, dividende, frais, versement), `Position` (calculée), `Valuation`.
- Règles métier testées unitairement : PRU, plus/moins-values latentes et réalisées, performance (TWR/MWR simple), éligibilité PEA, plafond de versement.
- **Saisie des données** : manuelle + import CSV (export courtier). L'intégration bancaire directe est reportée (Phase 9).
- **Données de marché** : port `MarketDataProvider` (cours actuels + historiques), adaptateur choisi via ADR, cache en base + job de rafraîchissement planifié (`@Scheduled` / ShedLock pour éviter les doubles exécutions).
- **Dashboard Angular** : valeur totale, répartition (par compte, secteur, géographie), courbe d'évolution, tableau des positions, P&L.
- Tests : unitaires domaine, intégration Testcontainers, E2E Playwright du parcours dashboard.

---

## Phase 3 — Agent conversationnel (LangChain4j)

- Choix du/des modèle(s) et fournisseur(s) via ADR à ce moment-là ; abstraction LangChain4j pour pouvoir changer.
- **Profil investisseur** : objectifs, horizon (court/moyen/long), tolérance au risque, persistés et injectés dans le contexte de l'agent.
- **AI Service LangChain4j** avec :
  - System prompt versionné (fichier ressource, pas en dur), disclaimer "pas un conseil financier".
  - **Tools** (lecture d'abord) : `getPortfolio`, `getPositions`, `getQuote`, `getPriceHistory`, `getInvestorProfile`, `getLatestNews`.
  - Mémoire de conversation persistée en base (ChatMemoryStore), historique des conversations.
  - Sortie structurée pour les recommandations (ticker, horizon, rationale, niveau de confiance).
  - **Guardrails** : validation des entrées/sorties, limites de tokens/coût, pas d'action d'écriture sans confirmation utilisateur.
- **Streaming** des réponses (SSE) vers Angular ; UI chat avec affichage des appels de tools.
- Chat accessible depuis toute l'app (panneau latéral persistant).
- **Observabilité LLM** : traces OpenTelemetry des appels modèle/tools (tokens, latence, coût) + Langfuse (self-hosted dans le compose) pour le suivi des prompts et sessions.
- Tests : tools testés unitairement, agent testé avec modèle mocké (réponses déterministes), quelques tests "live" taggés hors CI standard.

---

## Phase 4 — Simulation (portefeuilles fictifs)

- Domaine `SimulatedPortfolio` : capital initial fictif, type (PEA/CTO, avec règles d'éligibilité), ordres datés (dans le passé = backtest sur cours historiques, ou "à partir d'aujourd'hui" = suivi prospectif), frais paramétrables.
- Moteur de valorisation réutilisant le domaine `portfolio` (pas de duplication de logique).
- Comparaison à un benchmark (ex. CAC 40, MSCI World) et entre simulations.
- **Origine tracée** : chaque simulation sait si elle a été créée par l'utilisateur ou par l'agent (+ lien vers la conversation / la recommandation source).
- **Tools agent en écriture** : `createSimulation`, `addSimulatedOrder`, `getSimulationPerformance` — avec confirmation utilisateur dans l'UI avant exécution (human-in-the-loop).
- Page Angular : liste, création, détail avec courbe de performance vs benchmark.

---

## Phase 5 — Page Actu & agent planifié

- Ingestion de news (sources choisies via ADR : flux RSS financiers, API de news) via un port `NewsProvider`.
- **Job planifié** (Spring `@Scheduled` + ShedLock, ex. chaque matin avant ouverture) qui lance un agent :
  1. récupère et déduplique les news,
  2. résume / classe par valeur et secteur, sentiment,
  3. produit une "sélection du moment" structurée (titre, thèse, horizon, risques, sources citées).
- Stockage des rapports, page Actu (rapport du jour, historique, filtres).
- **Boucle d'évaluation** : chaque "action du moment" peut générer automatiquement une simulation suivie → mesure objective de la pertinence dans le temps.
- Coût maîtrisé : budget tokens par exécution, modèle moins cher pour le tri, frontier pour la synthèse.

---

## Phase 6 — MCP

- **Serveur MCP** exposant les capacités d'InvestAI (portefeuille, simulations, news) → utilisables depuis Claude Desktop / Claude Code.
- **Client MCP** côté agent LangChain4j pour consommer des serveurs MCP externes (données financières, recherche web, etc.).
- Sécurisation : auth sur le serveur MCP, liste blanche des tools exposés, journalisation des appels.

---

## Phase 7 — Évaluations & qualité IA

- Dataset de cas de test (questions + attentes) versionné dans le repo.
- Evals automatiques : exactitude des appels de tools, respect du profil/horizon, présence du disclaimer, hallucination de chiffres (comparaison aux données réelles des tools), LLM-as-judge pour la qualité.
- Exécution dans Langfuse (datasets/experiments) + workflow GitHub Actions déclenché quand un prompt ou la config agent change → régression bloquante.
- Dashboard de pertinence des conseils : performance des simulations créées par l'agent vs benchmark.

---

## Phase 8 — Durcissement

- Tests de charge légers (Gatling) sur le chat et le dashboard.
- Revue sécurité complète : OWASP ASVS niveau 1, OWASP Top 10 LLM (prompt injection via news, exfiltration via tools), DAST (OWASP ZAP baseline en CI).
- Rate limiting, gestion des pannes fournisseurs (retry, fallback modèle, circuit breaker Resilience4j).

---

## Phase 9 — Intégration bancaire / courtier (à cadrer le moment venu)

- Étudier les options (agrégateurs DSP2, export/API courtier, import fichiers).
- Adaptateur derrière un port `BrokerSyncProvider` : le domaine ne change pas.

## Phase 10 — Déploiement cloud (à cadrer le moment venu)

- Choix hébergeur via ADR, images signées, IaC, HTTPS, OIDC, sauvegardes Postgres, secrets managés, CD GitHub Actions avec environnements protégés.

---

## Méthode de travail avec les coding agents (toutes phases)

1. Chaque feature commence par une **spec courte** (issue GitHub ou `docs/specs/`) + critères d'acceptation.
2. Plan mode → validation → implémentation **test-first** sur une branche dédiée.
3. `/check` local vert → PR → CI + review agent (`/code-review`, `/security-review`) → merge.
4. Toute décision structurante → ADR.
5. Mise à jour de `CLAUDE.md` dès qu'une erreur récurrente d'agent est constatée (les consignes évoluent avec le projet).

## Vérification

- **Phase 0** : PR piège qui doit être bloquée par chaque contrôle (format, Checkstyle, SpotBugs/FindSecBugs, couverture, gitleaks, CodeQL/Semgrep, commitlint). `./mvnw verify` et `npm run lint && npm test` exécutables localement et en CI.
- **Chaque phase suivante** : `docker compose up` → parcours E2E Playwright de la feature, traces visibles dans Grafana (et Langfuse dès la Phase 3), CI verte, quality gate SonarCloud passé.
- **Phases IA** : suite d'evals passant au-dessus du seuil défini, coût par conversation mesuré.

## Prochaine étape concrète

Démarrer la Phase 0 : créer la structure du monorepo, `CLAUDE.md`, la configuration Maven/npm des outils qualité, Lefthook, puis les workflows GitHub Actions, et valider avec la PR piège.
