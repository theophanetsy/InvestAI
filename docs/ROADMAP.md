# Plan de développement — InvestAI

## Contexte

InvestAI est un projet perso à double objectif :
1. **Apprendre** Java / Spring Boot / Angular (compétences manquantes) et le développement assisté par coding agents.
2. **Intégrer de l'IA dans une web app** de façon "production-grade" : agent conversationnel, tools, MCP, modèles frontier, observabilité LLM, évaluations, tests.

Fonctionnel : **aide à la décision pour placer une somme unique sur un PEA**. L'agent de conseil propose des valeurs (actions, ETF) et une stratégie cohérente avec le profil de risque, à partir de l'historique des cours et des news ; le propriétaire passe lui-même les ordres chez son courtier (BoursoBank). Page **Actu** alimentée par un agent planifié qui analyse les news boursières ; page **Simulation** (portefeuilles fictifs) où chaque recommandation de l'agent est suivie et comparée à un ETF MSCI World, pour mesurer objectivement la pertinence de ses conseils.

Limite assumée : aucun modèle ne prédit de façon fiable quelles actions vont monter. L'application vaut par la cohérence de la stratégie, la mesure du risque sur des chiffres calculés (pas inventés par le modèle) et la notation des conseils dans le temps.

Décisions prises :
- **Stack** : Java 25 + Spring Boot 4, Angular 22 (standalone, signals, zoneless), PostgreSQL 18, monorepo ([ADR-0001](adr/0001-stack-technique.md)).
- **IA** : LangChain4j, versions beta acceptées (projet POC) ([ADR-0002](adr/0002-framework-ia-langchain4j.md)). Choix des modèles/fournisseurs reporté à la Phase 3.
- **Hébergement** : local d'abord, cloud en dernière phase.
- **Repo/CI** : GitHub + GitHub Actions.
- **Données de portefeuille** : lignes détenues et montant à placer déclarés directement, sans suivi des transactions ni import (recentrage du 2026-10-19). Pas de connexion directe à la banque (voir Phase 9).

Principe directeur : **chaque phase se termine par une app qui tourne, CI verte, quality gate passé**. Aucune ligne de code métier avant que la Phase 0 soit en place.

## Avancement

| Étape | Statut |
|---|---|
| Phase 0 — Garde-fous qualité | ✅ Terminée (PR #1) |
| Phase 1.1 — PostgreSQL, Flyway, Testcontainers | ✅ Terminée (PR #5) |
| Environnement reproductible pour agents cloud | ✅ Terminé (PR #6) |
| Phase 1.2 — Erreurs RFC 9457 + logs JSON | ✅ Terminée (PR #9) |
| Phase 1.3 — Authentification mono-utilisateur | ✅ Terminée (PR #10) |
| Phase 1.4 — Contrat OpenAPI + client Angular généré | ✅ Terminée (PR #11) |
| Phase 1.5 — Shell Angular | ✅ Terminée (PR #12) |
| Phase 1.6 — Agent `app-verifier` | ✅ Terminée |
| **Phase 2 — Données, profil et indicateurs** | 🔄 En cours |
| Phase 2.0 — Choix des données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md), [spec](specs/phase-2-00-market-data.md)) | ✅ Terminée (PR #16) |
| Phase 2.1 — Données de marché : référentiel, collecte quotidienne, historique ([spec](specs/phase-2-01-market-data-collection.md)) | ✅ Terminée (PR #17) ; vérification réelle avec la clé EODHD à faire par le propriétaire |
| Phase 2.2 — Clôtures de toutes les valeurs de Paris ([spec](specs/phase-2-02-all-paris-closes.md)) | ✅ Terminée (PR #19) |
| Phase 2.3 — Profil investisseur, portefeuille déclaré, éligibilité PEA ([spec](specs/phase-2-03-profile-portfolio.md)) | ✅ Terminée (PR #20) ; vérifiée dans la vraie application pendant la 2.4 (anomalies mineures : #21) |
| Phase 2.4 — Indicateurs calculés et dashboard ([spec](specs/phase-2-04-indicators-dashboard.md)) | ✅ Terminée (PR #22) |

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

## Phase 1 — Socle technique ✅

Objectif : le minimum de plomberie pour attaquer le métier. Ce qui ne sert qu'à partir de l'IA (observabilité) ou du cloud (images Docker de l'app) est volontairement repoussé.

| Étape | Contenu |
|---|---|
| 1.1 ✅ | PostgreSQL 18 via Docker Compose, Flyway, `ddl-auto=validate`, Testcontainers, règles ArchUnit JPA ([ADR-0005](adr/0005-persistance-postgres-flyway.md)). |
| 1.2 ✅ | Gestion d'erreurs uniforme (RFC 9457 Problem Details), validation des entrées, logs JSON structurés avec identifiant de corrélation. |
| 1.3 ✅ | **Auth** mono-utilisateur ([ADR-0007](adr/0007-authentification-mono-utilisateur.md), [spec](specs/phase-1-03-authentication.md)) : session `HttpOnly` + CSRF, identifiants hors du code, 401/403 et `/error` en problem+json, readiness `show-components=when-authorized`. OIDC prévu pour le cloud (Phase 10). |
| 1.4 ✅ | Contrat OpenAPI versionné (`api/openapi.json`, springdoc) + client Angular généré par openapi-generator, vérifiés en CI ([ADR-0004](adr/0004-api-contract-first.md), [spec](specs/phase-1-04-openapi-contract.md)). |
| 1.5 ✅ | Shell Angular ([ADR-0008](adr/0008-bibliotheque-ui-angular-material.md) : Angular Material, [spec](specs/phase-1-05-angular-shell.md)) : barre d'application et navigation, routes lazy (dashboard / assistant / actu / simulation, pages « Bientôt disponible »), écran de connexion + gardes, intercepteurs d'erreurs (401 → connexion, 403 CSRF → nouvelle tentative, 5xx → notification). |
| 1.6 ✅ | Agent `app-verifier` ([spec](specs/phase-1-06-app-verifier.md)) : `npm run stack:start` lance une copie isolée et jetable de l'app (base, backend, frontend, login aléatoire), l'agent la pilote avec Playwright MCP (`/verify-app`). |

En local, l'app tourne avec `./mvnw spring-boot:run` (qui démarre Postgres) et `npm start` : pas d'image Docker de l'app avant la Phase 10.

---

## Phase 2 — Données, profil et indicateurs

**Recentrage du 2026-10-19** : le propriétaire place une somme unique sur son PEA et passe ses ordres
lui-même ; ce qu'il attend de l'application, c'est le conseil (quelles valeurs, quelle stratégie,
quel risque), pas la comptabilité du portefeuille. La saisie des transactions, le PRU au centime
près, l'import CSV et l'import de documents par IA sont abandonnés ; la Phase 2 prépare les
données dont l'agent de conseil (Phase 3) a besoin. Une ébauche de la 2.2 « comptes et
transactions » est conservée hors de `main` (stash `phase 2.2 accounts/transactions WIP`).

| Étape | Contenu |
|---|---|
| 2.0 ✅ | Choix des sources de données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md)). |
| 2.1 ✅ | Module `market` ([spec](specs/phase-2-01-market-data-collection.md)) : référentiel EODHD de Paris (1 appel/semaine), clôtures quotidiennes depuis les fichiers MiFIR d'Euronext (job horaire 9 h-23 h et au démarrage), 1 an d'historique et rattrapage des jours manqués par EODHD dans un budget de 15 appels/jour, API de lecture et de suivi (`/api/v1/market`). Jobs `@Scheduled` sans ShedLock (une seule instance). |
| 2.2 ✅ | **Tout Paris** ([spec](specs/phase-2-02-all-paris-closes.md)) : la clôture de chaque valeur du référentiel (actions et ETF, ~1 300) est gardée chaque jour depuis les fichiers Euronext, sans coût, avec le volume et le nombre de transactions de la séance ; l'historique se constitue au fil des séances (trous les jours sans collecte). L'historique EODHD d'1 an et le rattrapage restent réservés aux valeurs suivies (budget de 15 appels/jour). |
| 2.3 ✅ | **Éligibilité PEA** des valeurs (déplacée de la 2.2, règle validée par le propriétaire). **Profil et portefeuille déclaré** : somme unique à placer (en une fois, sans versements réguliers), horizon, profil de risque choisi parmi quatre (prudent, équilibré, dynamique, offensif), préférences, date d'ouverture du PEA ; lignes détenues saisies directement (valeur, quantité, prix moyen d'achat affiché par le courtier), sans historique de transactions ; valorisation sur la dernière clôture. Recherche de valeurs dans l'UI. |
| 2.4 ✅ | **Indicateurs calculés** ([spec](specs/phase-2-04-indicators-dashboard.md), en base, jamais par le modèle) : performance sur 1/3/6/12 mois, volatilité, pire baisse, comparaison au MSCI World (CW8) et au CAC 40, liquidité ; calcul quotidien de tout Paris pour le filtrer ; concentration et corrélations du portefeuille déclaré. Dashboard Angular simple : portefeuille, indicateurs, courbe comparée à CW8 et à l'ETF CAC 40. Limite assumée : clôtures brutes, **pas d'ajustement des opérations sur titres** (divisions, attributions gratuites) ni des dividendes. |

- **2.0 ✅ — Données de marché d'abord (ADR + spike)** : c'est la dépendance critique (dashboard, simulations, backtests, tools de l'agent). Valider sur des valeurs d'Euronext Paris : cours actuels, historique profond, couverture des ETF, quotas, conditions d'utilisation. Benchmarks via des ETF cotés (CAC 40, MSCI World) plutôt que des données d'indices, souvent sous licence. Décision ([ADR-0009](adr/0009-fournisseur-donnees-marche.md)) : clôtures quotidiennes depuis les fichiers différés gratuits d'Euronext (MiFIR), référentiel / 1 an d'historique / opérations sur titres depuis l'offre gratuite d'EODHD.
- **Données de marché** : port `MarketDataProvider` (cours actuels + historiques), adaptateurs Euronext MiFIR et EODHD (ADR-0009), cache en base + jobs de collecte planifiés (`@Scheduled`) dans le backend. ShedLock est reporté au déploiement multi-instances (Phase 10) : une seule instance tourne, l'idempotence repose sur les contraintes d'unicité (spec 2.1, critère 20). Le dashboard, les simulations et les tools de l'agent lisent la base, jamais les fournisseurs.
- Tests : unitaires domaine, intégration Testcontainers, E2E Playwright des parcours profil / portefeuille / dashboard.

---

## Phase 3 — Agent de conseil (LangChain4j)

### 3.0 — Socle IA
- **ADR choix du/des modèle(s) et fournisseur(s)** : qualité, coût (budget mensuel à fixer avec le propriétaire), rétention des données (profil et portefeuille sont des données personnelles).
- LangChain4j confiné à `..infrastructure..` (règle ArchUnit, comme JPA) : le domaine ne dépend jamais du framework.
- **Observabilité** : Micrometer + OpenTelemetry → Grafana LGTM (un conteneur dans le compose) ; traces corrélées front → back → DB → appels modèle/tools (tokens, latence, coût). Langfuse (self-hosted) pour le suivi des prompts et sessions.
- **Harnais d'evals dès le départ** : dataset versionné dans le repo, exécuté comme tests taggés (hors CI standard, car payants). Chaque changement de prompt se juge sur les evals, pas au feeling.
- **Modèle de menace IA** (OWASP Top 10 LLM) rédigé avant le premier tool : données non fiables, tools d'écriture, exfiltration.

### 3.1 — Agent conversationnel
- Rôle : proposer des valeurs et une **stratégie cohérente avec le profil** (2.3) pour la somme unique
  à placer (répartition, diversification, part d'ETF / d'actions décidée avec l'agent selon le
  profil), en limitant le risque ; justifier chaque proposition par les indicateurs calculés (2.4),
  jamais par des chiffres qu'il produirait lui-même.
- **AI Service LangChain4j** avec :
  - System prompt versionné (fichier ressource, pas en dur), disclaimer "pas un conseil financier".
  - **Tools** (lecture uniquement) : `getPortfolio`, `getInvestorProfile`, `searchInstruments`, `getQuote`, `getPriceHistory`, `getIndicators`, `getLatestNews` (à partir de la Phase 4).
  - Mémoire de conversation persistée en base (ChatMemoryStore), historique des conversations.
  - Sortie structurée pour les recommandations (valeur, montant ou poids, horizon, justification, risques, niveau de confiance), **enregistrée et datée** : elle sera notée en Phase 5.
  - **Guardrails** : validation des entrées/sorties, limites de tokens/coût, pas d'action d'écriture sans confirmation utilisateur.
- **Streaming** des réponses (SSE) vers Angular ; UI chat avec affichage des appels de tools.
- Chat accessible depuis toute l'app (panneau latéral persistant).
- Tests : tools testés unitairement, agent testé avec modèle mocké (réponses déterministes), evals sur un modèle réel (appels de tools attendus, respect de l'horizon, présence du disclaimer).

---

## Phase 4 — Page Actu & agent planifié

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

## Phase 5 — Simulation et notation des conseils (portefeuilles fictifs)

- Domaine `SimulatedPortfolio` : capital initial fictif (par défaut la somme unique du profil), règles d'éligibilité PEA, ordres datés (dans le passé = backtest sur cours historiques, limité par la profondeur d'historique ; ou "à partir d'aujourd'hui" = suivi prospectif), frais paramétrables.
- Valorisation partagée avec le portefeuille déclaré (2.3), sans duplication de logique.
- **Chaque recommandation de l'agent (3.1) devient un portefeuille fictif suivi**, comparé à l'ETF MSCI World acheté au même moment avec le même montant : c'est la mesure objective de sa valeur.
- **Origine tracée** : chaque simulation sait si elle a été créée par l'utilisateur, par l'agent ou par le code applicatif (+ lien vers la conversation / la recommandation source).
- **Tools agent en écriture** : `createSimulation`, `addSimulatedOrder`, `getSimulationPerformance` — avec confirmation utilisateur dans l'UI avant exécution (human-in-the-loop).
- Page Angular : liste, création, détail avec courbe de performance vs benchmark.

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

Le portefeuille déclaré (2.3) couvre le besoin. Si une API officielle apparaît : adaptateur derrière un port `BrokerSyncProvider`, le domaine ne change pas.

## Phase 10 — Déploiement cloud (à cadrer le moment venu)

- Images Docker du backend et du frontend (hadolint, Trivy), signées.
- Choix hébergeur via ADR, IaC, HTTPS, OIDC, sauvegardes Postgres, secrets managés, CD GitHub Actions avec environnements protégés.
- ShedLock sur les jobs planifiés (collecte des données de marché, agent Actu) dès que plusieurs instances tournent.

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
