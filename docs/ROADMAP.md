# Plan de développement — InvestAI

## Contexte

InvestAI est un projet perso à double objectif :
1. **Apprendre** Java / Spring Boot / Angular (compétences manquantes) et le développement assisté par coding agents.
2. **Intégrer de l'IA dans une web app** de façon "production-grade" : agent conversationnel, tools, MCP, modèles frontier, observabilité LLM, évaluations, tests.

Fonctionnel : suivi PEA + compte-titres (dashboard), agent de conseil toujours disponible (selon horizon court/moyen/long terme), page **Actu** alimentée par un agent planifié qui analyse les news boursières, page **Simulation** (portefeuilles fictifs, achats datés, suivi de performance) — simulations créables par l'agent depuis le chat, pour mesurer la pertinence de ses conseils.

Décisions prises :
- **Stack** : Java 25 + Spring Boot 4, Angular 22 (standalone, signals, zoneless), PostgreSQL 18, monorepo ([ADR-0001](adr/0001-stack-technique.md)).
- **IA** : LangChain4j, versions beta acceptées (projet POC) ([ADR-0002](adr/0002-framework-ia-langchain4j.md)). Choix des modèles/fournisseurs reporté à la Phase 3.
- **Hébergement** : local d'abord, cloud en dernière phase.
- **Repo/CI** : GitHub + GitHub Actions.
- **Données de portefeuille** : saisie manuelle + import de documents (CSV, avis d'opéré, relevés). Pas de connexion directe à la banque (voir Phase 9).

Principe directeur : **chaque phase se termine par une app qui tourne, CI verte, quality gate passé**. Aucune ligne de code métier avant que la Phase 0 soit en place.

## Avancement

| Étape | Statut |
|---|---|
| Phase 0 — Garde-fous qualité | ✅ Terminée (PR #1) |
| Phase 1.1 — PostgreSQL, Flyway, Testcontainers | ✅ Terminée (PR #5) |
| Environnement reproductible pour agents cloud | ✅ Terminé (PR #6) |
| Phase 1.2 — Erreurs RFC 9457 + logs JSON | ✅ Terminée (PR #9) |
| Phase 1.3 — Authentification mono-utilisateur | ✅ Terminée |
| **Phase 1.4** | ⏭️ Prochaine étape |

---

## Phase 0 — Garde-fous qualité & consignes pour coding agents ✅

Objectif : un repo où il est *impossible* de merger du code non formaté, non testé, vulnérable ou hors conventions — que l'auteur soit humain ou agent. Détail des outils : [ADR-0003](adr/0003-garde-fous-qualite.md).

- **Consignes agents** : `CLAUDE.md` (racine, `backend/`, `frontend/`), `AGENTS.md`, Definition of Done, interdits.
- **Claude Code** : hooks (format auto, protection des configs qualité, vérification en fin de tour), commandes `/check`, `/new-feature`, `/new-adr`, sous-agents reviewers Java / Angular / sécurité.
- **Backend** : Spotless, Checkstyle, PMD, SpotBugs + FindSecBugs, Error Prone + NullAway, ArchUnit, JaCoCo (80 % / 70 %), PIT (nightly), Maven Enforcer.
- **Frontend** : ESLint strict (angular-eslint + typescript-eslint), Prettier, Stylelint, TypeScript strict, Vitest avec seuils, Playwright.
- **Transverse** : Lefthook (gitleaks, format, lint, commitlint), CodeQL, Semgrep, OSV-Scanner, Trivy, dependency review, Dependabot (cooldown 7 j), ruleset `protect-main` avec 13 checks requis.
- **Sortie de phase** : PR pièges bloquées par chaque contrôle (PR #2, #3).

---

## Phase 1 — Socle technique

Objectif : le minimum de plomberie pour attaquer le métier. Ce qui ne sert qu'à partir de l'IA (observabilité) ou du cloud (images Docker de l'app) est volontairement repoussé.

| Étape | Contenu |
|---|---|
| 1.1 ✅ | PostgreSQL 18 via Docker Compose, Flyway, `ddl-auto=validate`, Testcontainers, règles ArchUnit JPA ([ADR-0005](adr/0005-persistance-postgres-flyway.md)). |
| 1.2 ✅ | Gestion d'erreurs uniforme (RFC 9457 Problem Details), validation des entrées, logs JSON structurés avec identifiant de corrélation. |
| 1.3 ✅ | **Auth** mono-utilisateur ([ADR-0007](adr/0007-authentification-mono-utilisateur.md), [spec](specs/phase-1-03-authentication.md)) : session `HttpOnly` + CSRF, identifiants hors du code, 401/403 et `/error` en problem+json, readiness `show-components=when-authorized`. OIDC prévu pour le cloud (Phase 10). |
| 1.4 | Contrat OpenAPI (springdoc) + client Angular généré, vérifié en CI ([ADR-0004](adr/0004-api-contract-first.md)). |
| 1.5 | Shell Angular : layout, routing lazy (dashboard / chat / actu / simulation), lib UI (ADR : Angular Material ou PrimeNG), gestion des erreurs HTTP, écran de connexion + intercepteur 401. Agent `app-verifier` (lance la stack locale et explore l'UI avec Playwright) une fois l'UI en place. |

En local, l'app tourne avec `./mvnw spring-boot:run` (qui démarre Postgres) et `npm start` : pas d'image Docker de l'app avant la Phase 10.

---

## Phase 2 — Portefeuilles réels & Dashboard

- **2.0 — Données de marché d'abord (ADR + spike)** : c'est la dépendance critique (dashboard, simulations, backtests, tools de l'agent). Valider sur des valeurs d'Euronext Paris : cours actuels, historique profond, couverture des ETF, quotas, conditions d'utilisation. Benchmarks via des ETF cotés (CAC 40, MSCI World) plutôt que des données d'indices, souvent sous licence.
- **Modèle de domaine** : `Account` (type PEA / CTO, plafond de versements PEA), `Instrument` (ISIN, ticker, place, devise, éligibilité PEA), `Transaction` (achat, vente, dividende, frais, versement), `Position` (calculée), `Valuation`.
- Règles métier testées unitairement : PRU, plus/moins-values latentes et réalisées, performance (TWR/MWR simple), éligibilité PEA, plafond de versement.
- **Saisie des données** : manuelle + import CSV. L'import de documents par IA arrive en Phase 3.
- **Données de marché** : port `MarketDataProvider` (cours actuels + historiques), adaptateur retenu en 2.0, cache en base + job de rafraîchissement planifié (`@Scheduled` + ShedLock).
- **Dashboard Angular** : valeur totale, répartition (par compte, secteur, géographie), courbe d'évolution, tableau des positions, P&L.
- Tests : unitaires domaine, intégration Testcontainers, E2E Playwright du parcours dashboard.

---

## Phase 3 — Premiers pas IA : import intelligent puis agent conversationnel (LangChain4j)

### 3.0 — Socle IA
- **ADR choix du/des modèle(s) et fournisseur(s)** : qualité, coût, rétention des données (les documents importés contiennent des données personnelles : masquer le numéro de compte avant envoi).
- LangChain4j confiné à `..infrastructure..` (règle ArchUnit, comme JPA) : le domaine ne dépend jamais du framework.
- **Observabilité** : Micrometer + OpenTelemetry → Grafana LGTM (un conteneur dans le compose) ; traces corrélées front → back → DB → appels modèle/tools (tokens, latence, coût). Langfuse (self-hosted) pour le suivi des prompts et sessions.
- **Harnais d'evals dès le départ** : dataset versionné dans le repo, exécuté comme tests taggés (hors CI standard, car payants). Chaque changement de prompt se juge sur les evals, pas au feeling.
- **Modèle de menace IA** (OWASP Top 10 LLM) rédigé avant le premier tool : données non fiables, tools d'écriture, exfiltration.

### 3.1 — Import de documents par IA (premier cas d'usage)
- Upload d'un document (avis d'opéré, relevé de portefeuille, export CSV) → appel unique au modèle → **sortie structurée** (transactions candidates).
- Validation stricte (ISIN, montants `BigDecimal`, devise, dates) puis **écran de confirmation** : rien n'est écrit sans validation de l'utilisateur.
- Evals naturelles : la vérité terrain est connue pour chaque document (exactitude champ par champ).
- Remplace la connexion bancaire directe (voir Phase 9).

### 3.2 — Agent conversationnel
- **Profil investisseur** : objectifs, horizon (court/moyen/long), tolérance au risque, persistés et injectés dans le contexte de l'agent.
- **AI Service LangChain4j** avec :
  - System prompt versionné (fichier ressource, pas en dur), disclaimer "pas un conseil financier".
  - **Tools** (lecture d'abord) : `getPortfolio`, `getPositions`, `getQuote`, `getPriceHistory`, `getInvestorProfile`, `getLatestNews`.
  - Mémoire de conversation persistée en base (ChatMemoryStore), historique des conversations.
  - Sortie structurée pour les recommandations (ticker, horizon, rationale, niveau de confiance).
  - **Guardrails** : validation des entrées/sorties, limites de tokens/coût, pas d'action d'écriture sans confirmation utilisateur.
- **Streaming** des réponses (SSE) vers Angular ; UI chat avec affichage des appels de tools.
- Chat accessible depuis toute l'app (panneau latéral persistant).
- Tests : tools testés unitairement, agent testé avec modèle mocké (réponses déterministes), evals sur un modèle réel (appels de tools attendus, respect de l'horizon, présence du disclaimer).

---

## Phase 4 — Simulation (portefeuilles fictifs)

- Domaine `SimulatedPortfolio` : capital initial fictif, type (PEA/CTO, avec règles d'éligibilité), ordres datés (dans le passé = backtest sur cours historiques, ou "à partir d'aujourd'hui" = suivi prospectif), frais paramétrables.
- Moteur de valorisation réutilisant le domaine `portfolio` (pas de duplication de logique).
- Comparaison à un benchmark (ETF CAC 40, MSCI World) et entre simulations.
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
- **Isolation contre l'injection de prompt indirecte** : les news sont du contenu non fiable. L'agent Actu n'a **aucun tool** en dehors de l'écriture de son rapport (pas d'accès au portefeuille, pas de création de simulation). L'agent du chat ne consomme ce rapport que comme une donnée, jamais comme des instructions.
- Stockage des rapports, page Actu (rapport du jour, historique, filtres).
- **Boucle d'évaluation** : chaque "action du moment" peut générer une simulation suivie (créée par le code applicatif, pas par l'agent) → mesure objective de la pertinence dans le temps.
- Coût maîtrisé : budget tokens par exécution, modèle moins cher pour le tri, frontier pour la synthèse.

---

## Phase 6 — MCP

- **Serveur MCP** exposant les capacités d'InvestAI (portefeuille, simulations, news) → utilisables depuis Claude Desktop / Claude Code.
- **Client MCP** côté agent LangChain4j pour consommer des serveurs MCP externes (données financières, recherche web, etc.).
- Sécurisation : auth sur le serveur MCP, liste blanche des tools exposés, journalisation des appels.

---

## Phase 7 — Évaluations avancées

Les evals existent depuis la Phase 3 ; cette phase les industrialise.

- Hallucination de chiffres (comparaison aux données réelles des tools), LLM-as-judge pour la qualité des réponses.
- Exécution dans Langfuse (datasets/experiments) + workflow GitHub Actions déclenché quand un prompt ou la config agent change → régression bloquante.
- Dashboard de pertinence des conseils : performance des simulations créées par l'agent vs benchmark.

---

## Phase 8 — Durcissement

- Tests de charge légers (Gatling) sur le chat et le dashboard.
- Revue sécurité complète : OWASP ASVS niveau 1, audit du modèle de menace IA (Phase 3) face au code réel, DAST (OWASP ZAP baseline en CI).
- Rate limiting, gestion des pannes fournisseurs (retry, fallback modèle, circuit breaker Resilience4j).

---

## Phase 9 — Synchronisation directe avec le courtier (optionnelle)

Non prioritaire, et peut-être jamais :
- la DSP2 n'impose l'accès qu'aux **comptes de paiement** : les PEA et comptes-titres n'en font pas partie, un agrégateur ne garantit donc pas l'accès aux positions ;
- le scraping de l'espace client est exclu (conditions d'utilisation, double authentification, fragilité, stockage des identifiants bancaires).

L'import de documents (Phase 3.1) couvre le besoin. Si une API officielle apparaît : adaptateur derrière un port `BrokerSyncProvider`, le domaine ne change pas.

## Phase 10 — Déploiement cloud (à cadrer le moment venu)

- Images Docker du backend et du frontend (hadolint, Trivy), signées.
- Choix hébergeur via ADR, IaC, HTTPS, OIDC, sauvegardes Postgres, secrets managés, CD GitHub Actions avec environnements protégés.

---

## Méthode de travail avec les coding agents (toutes phases)

1. Chaque feature commence par une **spec courte** (issue GitHub ou `docs/specs/`) + critères d'acceptation.
2. Plan mode → validation → implémentation **test-first** sur une branche dédiée.
3. `/check` local vert → PR → CI + review agent (`/code-review`, `/security-review`) → merge.
4. Toute décision structurante → ADR.
5. Mise à jour de `CLAUDE.md` dès qu'une erreur récurrente d'agent est constatée (les consignes évoluent avec le projet).

## Vérification

- **Phase 0** : PR pièges bloquées par chaque contrôle ✅.
- **Chaque phase suivante** : parcours E2E Playwright de la feature, CI verte, quality gate SonarCloud passé ; traces visibles dans Grafana et Langfuse à partir de la Phase 3.
- **Phases IA** : suite d'evals au-dessus du seuil défini, coût par conversation mesuré.
