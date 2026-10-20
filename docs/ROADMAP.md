# Plan de développement — InvestAI

## Contexte

InvestAI est un projet perso à double objectif :
1. **Apprendre** Java / Spring Boot / Angular (compétences manquantes) et le développement assisté par coding agents.
2. **Intégrer de l'IA dans une web app** de façon "production-grade" : serveur MCP et ses outils, consignes de conseil, sécurité face aux données non fiables (injection de prompt), observabilité, tests, sans facture d'API ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md)).

Fonctionnel : **aide à la décision pour placer une somme unique sur un PEA**.

- **Conseil** : Claude, dans Claude Desktop ou Claude Code (abonnement du propriétaire), est branché sur le **serveur MCP** de l'application. Il propose des valeurs (actions, ETF de Paris) et une stratégie cohérente avec le profil de risque. Il s'appuie sur les chiffres calculés par l'application et sur les news collectées. Le propriétaire passe lui-même les ordres chez son courtier (BoursoBank).
- **Page Actu** : elle liste les news collectées par l'application. Leur analyse est lancée à la main depuis Claude Desktop.
- **Page Simulation** (portefeuilles fictifs) : chaque recommandation enregistrée est suivie et comparée au MSCI World (CW8) et au CAC 40, pour mesurer objectivement la pertinence des conseils.

Limite assumée : aucun modèle ne prédit de façon fiable quelles actions vont monter. L'application vaut par la cohérence de la stratégie, la mesure du risque sur des chiffres calculés (pas inventés par le modèle) et la notation des conseils dans le temps.

Décisions prises :
- **Stack** : Java 25 + Spring Boot 4, Angular 22 (standalone, signals, zoneless), PostgreSQL 18, monorepo ([ADR-0001](adr/0001-stack-technique.md)).
- **IA** : **serveur MCP uniquement** ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md), accepté le 2026-10-20 ; remplace l'[ADR-0002](adr/0002-framework-ia-langchain4j.md) LangChain4j).
  - L'application n'appelle jamais l'API Anthropic : pas de clé d'API IA, pas de chat intégré, pas d'agent planifié. **Budget IA : 0 €** au-delà de l'abonnement Claude du propriétaire.
  - Le modèle est celui de l'abonnement, choisi dans le client.
- **Hébergement** : local d'abord, cloud en dernière phase.
- **Repo/CI** : GitHub + GitHub Actions.
- **Données de portefeuille** : lignes détenues et montant à placer déclarés directement, sans suivi des transactions ni import (recentrage du 2026-10-19). Pas de connexion directe à la banque (voir Phase 8).

**Replanification du 2026-10-20** (« MCP uniquement », ADR-0010) :

- L'ancienne Phase 6 (MCP) est fusionnée dans la Phase 3, qui devient « Conseil via MCP ».
- L'ancienne Phase 7 (évaluations avancées par l'API) est réécrite sans API : c'est la nouvelle Phase 6.
- Les anciennes phases 8, 9 et 10 deviennent les phases 7, 8 et 9.
- Quelques commentaires du code citent encore l'ancienne numérotation ou le chat. Ils seront corrigés avec le code qu'ils accompagnent, en Phase 3 :
  - « phase 10 » pour ShedLock dans `MarketJobs` ;
  - pages Angular « Bientôt disponible » de l'assistant, de l'Actu et de la Simulation.

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
| **Phase 2 — Données, profil et indicateurs** | ✅ Terminée |
| Phase 2.0 — Choix des données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md), [spec](specs/phase-2-00-market-data.md)) | ✅ Terminée (PR #16) |
| Phase 2.1 — Données de marché : référentiel, collecte quotidienne, historique ([spec](specs/phase-2-01-market-data-collection.md)) | ✅ Terminée (PR #17) ; vérification réelle avec la clé EODHD à faire par le propriétaire |
| Phase 2.2 — Clôtures de toutes les valeurs de Paris ([spec](specs/phase-2-02-all-paris-closes.md)) | ✅ Terminée (PR #19) |
| Phase 2.3 — Profil investisseur, portefeuille déclaré, éligibilité PEA ([spec](specs/phase-2-03-profile-portfolio.md)) | ✅ Terminée (PR #20) ; vérifiée dans la vraie application pendant la 2.4 (anomalies mineures : #21) |
| Phase 2.4 — Indicateurs calculés et dashboard ([spec](specs/phase-2-04-indicators-dashboard.md)) | ✅ Terminée (PR #22) ; points ouverts tranchés le 2026-10-20 (séance de référence, plausibilité reportée) |
| **Phase 3 — Conseil via MCP** ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md)) | ⏳ En cours : ADR-0010 accepté le 2026-10-20, 3.0 en cours |

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
| 1.3 ✅ | **Auth** mono-utilisateur ([ADR-0007](adr/0007-authentification-mono-utilisateur.md), [spec](specs/phase-1-03-authentication.md)) : session `HttpOnly` + CSRF, identifiants hors du code, 401/403 et `/error` en problem+json, readiness `show-components=when-authorized`. OIDC prévu pour le cloud (Phase 9). |
| 1.4 ✅ | Contrat OpenAPI versionné (`api/openapi.json`, springdoc) + client Angular généré par openapi-generator, vérifiés en CI ([ADR-0004](adr/0004-api-contract-first.md), [spec](specs/phase-1-04-openapi-contract.md)). |
| 1.5 ✅ | Shell Angular ([ADR-0008](adr/0008-bibliotheque-ui-angular-material.md) : Angular Material, [spec](specs/phase-1-05-angular-shell.md)) : barre d'application et navigation, routes lazy (dashboard / assistant / actu / simulation, pages « Bientôt disponible »), écran de connexion + gardes, intercepteurs d'erreurs (401 → connexion, 403 CSRF → nouvelle tentative, 5xx → notification). |
| 1.6 ✅ | Agent `app-verifier` ([spec](specs/phase-1-06-app-verifier.md)) : `npm run stack:start` lance une copie isolée et jetable de l'app (base, backend, frontend, login aléatoire), l'agent la pilote avec Playwright MCP (`/verify-app`). |

En local, l'app tourne avec `./mvnw spring-boot:run` (qui démarre Postgres) et `npm start` : pas d'image Docker de l'app avant la Phase 9.

---

## Phase 2 — Données, profil et indicateurs ✅

**Recentrage du 2026-10-19** : le propriétaire place une somme unique sur son PEA et passe ses
ordres lui-même. Ce qu'il attend de l'application, c'est le conseil (quelles valeurs, quelle
stratégie, quel risque), pas la comptabilité du portefeuille.

- Abandonnés : la saisie des transactions, le PRU au centime près, l'import CSV et l'import de
  documents par IA.
- La Phase 2 prépare les données dont le conseil via MCP (Phase 3) a besoin.
- Une ébauche de la 2.2 « comptes et transactions » est conservée hors de `main` (stash
  `phase 2.2 accounts/transactions WIP`).

| Étape | Contenu |
|---|---|
| 2.0 ✅ | Choix des sources de données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md)). |
| 2.1 ✅ | Module `market` ([spec](specs/phase-2-01-market-data-collection.md)) : référentiel EODHD de Paris (1 appel/semaine), clôtures quotidiennes depuis les fichiers MiFIR d'Euronext (job horaire 9 h-23 h et au démarrage), 1 an d'historique et rattrapage des jours manqués par EODHD dans un budget de 15 appels/jour, API de lecture et de suivi (`/api/v1/market`). Jobs `@Scheduled` sans ShedLock (une seule instance). |
| 2.2 ✅ | **Tout Paris** ([spec](specs/phase-2-02-all-paris-closes.md)) : la clôture de chaque valeur du référentiel (actions et ETF, ~1 300) est gardée chaque jour depuis les fichiers Euronext, sans coût, avec le volume et le nombre de transactions de la séance ; l'historique se constitue au fil des séances (trous les jours sans collecte). L'historique EODHD d'1 an et le rattrapage restent réservés aux valeurs suivies (budget de 15 appels/jour). |
| 2.3 ✅ | **Éligibilité PEA** des valeurs (déplacée de la 2.2, règle validée par le propriétaire). **Profil et portefeuille déclaré** : somme unique à placer (en une fois, sans versements réguliers), horizon, profil de risque choisi parmi quatre (prudent, équilibré, dynamique, offensif), préférences, date d'ouverture du PEA ; lignes détenues saisies directement (valeur, quantité, prix moyen d'achat affiché par le courtier), sans historique de transactions ; valorisation sur la dernière clôture. Recherche de valeurs dans l'UI. |
| 2.4 ✅ | **Indicateurs calculés** ([spec](specs/phase-2-04-indicators-dashboard.md), en base, jamais par le modèle) : performance sur 1/3/6/12 mois, volatilité, pire baisse, comparaison au MSCI World (CW8) et au CAC 40, liquidité ; calcul quotidien de tout Paris pour le filtrer ; concentration et corrélations du portefeuille déclaré. Dashboard Angular simple : portefeuille, indicateurs, courbe comparée à CW8 et à l'ETF CAC 40. Limite assumée : clôtures brutes, **pas d'ajustement des opérations sur titres** (divisions, attributions gratuites) ni des dividendes. Décisions du 2026-10-20 : la performance part de la première clôture de la fenêtre quand la séance de référence manque (comportement conservé) ; l'indicateur de plausibilité des cotations est reporté (Phase 7). |

- **2.0 ✅ — Données de marché d'abord (ADR + spike)** : c'est la dépendance critique (dashboard,
  simulations, backtests, outils MCP).
  - Points validés sur des valeurs d'Euronext Paris : cours actuels, historique profond, couverture
    des ETF, quotas, conditions d'utilisation.
  - Benchmarks via des ETF cotés (CAC 40, MSCI World) plutôt que des données d'indices, souvent
    sous licence.
  - Décision ([ADR-0009](adr/0009-fournisseur-donnees-marche.md)) : clôtures quotidiennes depuis
    les fichiers différés gratuits d'Euronext (MiFIR) ; référentiel, 1 an d'historique et
    opérations sur titres depuis l'offre gratuite d'EODHD.
- **Données de marché** : port `MarketDataProvider` (cours actuels et historiques), adaptateurs
  Euronext MiFIR et EODHD (ADR-0009), cache en base et jobs de collecte planifiés (`@Scheduled`)
  dans le backend.
  - ShedLock est reporté au déploiement multi-instances (Phase 9) : une seule instance tourne, et
    l'idempotence repose sur les contraintes d'unicité (spec 2.1, critère 20).
  - Le dashboard, les simulations et les outils MCP lisent la base, jamais les fournisseurs.
- Tests : unitaires domaine, intégration Testcontainers, E2E Playwright des parcours profil /
  portefeuille / dashboard.

---

## Phase 3 — Conseil via MCP

Le propriétaire branche Claude Desktop ou Claude Code (son abonnement) sur le serveur MCP de
l'application ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md)). Claude lit le profil, le
portefeuille et les indicateurs par des outils, puis conseille dans le client. L'application
n'appelle jamais de modèle.

### 3.0 — ADR-0010 et squelette du serveur MCP

- **ADR-0010 validé par le propriétaire** : implémentation, transport, authentification, règles
  d'usage.
- **Dépendance MCP** retenue par l'ADR (recommandation : starter Spring AI MCP Server WebMVC) :
  - version exacte épinglée ;
  - confinée à `..infrastructure..` par une règle ArchUnit, comme JPA ;
  - module `assistant`, qui appelle les cas d'usage existants, jamais les dépôts.
- **Transport et sécurité** (recommandation : Streamable HTTP sans état sur `/mcp`, dans le
  backend) :
  - écoute sur `127.0.0.1` et pare-feu `Host` (déjà en place), refus de toute requête portant un
    en-tête `Origin` ;
  - jeton porteur dédié : empreinte en variable d'environnement, script de génération, `/mcp` non
    monté sans configuration ;
  - cookie de session refusé sur `/mcp`, limite de débit ;
  - journalisation de chaque appel : outil, durée, statut, `requestId`, sans arguments en texte
    libre ni résultats.
- **Un outil de santé** (version, `asOf` des dernières clôtures, `computedAt` du dernier calcul
  des indicateurs) pour valider le branchement de bout en bout.
- **Branchement documenté** dans `docs/setup.md` quand le squelette existe :
  - Claude Code : `claude mcp add --transport http`, en portée utilisateur, **jamais dans le
    `.mcp.json` du dépôt** ;
  - Claude Desktop : passerelle stdio locale.
- **Observabilité des appels d'outils** : Micrometer et OpenTelemetry vers Grafana LGTM (un
  conteneur dans le compose), traces client MCP → outil → base corrélées au `requestId`. Les
  appels au modèle restent invisibles : ils se font côté Claude.
- **Modèle de menace IA** (OWASP Top 10 LLM), rédigé avant le premier outil métier dans
  `docs/security/` :
  - données non fiables (noms d'instruments, texte libre du profil, news) ;
  - exfiltration par les autres connecteurs du client ;
  - futur outil d'écriture (Phase 5).
- **Tests** d'intégration du point `/mcp` : 401 sans jeton, 403 avec `Origin`, cookie de session
  refusé, liste d'outils figée.

### 3.1 — Outils en lecture et consignes de conseil

- **Rôle de Claude** : proposer des valeurs et une **stratégie cohérente avec le profil** (2.3)
  pour la somme unique à placer, en limitant le risque.
  - Répartition et diversification ; la part d'ETF et d'actions se décide avec le propriétaire,
    selon le profil.
  - Chaque proposition est justifiée par les indicateurs calculés (2.4), jamais par des chiffres
    que Claude produirait lui-même.
- **Outils** (lecture seule, annotation `readOnlyHint`) :
  - **Format commun** :
    - projection dédiée, pas les DTO REST ;
    - contenu structuré avec un schéma de sortie, résultats bornés ;
    - **statut et dates `asOf` et `computedAt` toujours transmis**.
  - `getInvestorProfile` : somme à placer, horizon, profil de risque, préférences. Les objectifs
    et exclusions sont délimités comme données non fiables.
  - `getPortfolio` : lignes déclarées, valorisation et indicateurs du portefeuille (composition,
    risque, corrélations), sans la courbe.
  - `searchInstruments` : recherche par nom, ISIN ou mnémonique, avec l'éligibilité PEA ; noms
    délimités.
  - `getQuote` et `getPriceHistory` : dernière clôture et historique (borné).
  - `getIndicators` : indicateurs d'une valeur, chacun avec son statut.
  - `screenInstruments` : filtre de tout Paris (éligibilité, volatilité, liquidité, performance,
    tri, 50 résultats au plus). Critère action / ETF à ajouter si besoin (point ouvert de la 2.4).
- **Consignes de conseil** versionnées dans le dépôt : prompt MCP `conseil-placement`,
  instructions du serveur et descriptions des outils.
  - Profil d'abord : somme unique, horizon, risque, exclusions. Répartition ETF / actions décidée
    avec le propriétaire, diversification, frais.
  - Uniquement les chiffres de l'application, cités avec leur date.
  - Rappel des limites des données :
    - **clôtures brutes, sans ajustement des divisions ni des attributions gratuites**, hors
      dividendes ;
    - **une cotation aberrante peut placer une valeur en tête d'un tri par performance** ;
    - historique insuffisant ;
    - volatilité sous-estimée des valeurs peu échangées ; liquidité ;
    - éligibilité PEA indicative.
  - Textes de tiers et texte libre du profil = données, jamais instructions.
  - Avertissement : **pas un conseil en investissement réglementé**.
- **Tests** :
  - chaque outil, en unitaire et en intégration avec un vrai client MCP du SDK ;
  - jeux de cas sur les sorties : schéma, statuts, dates, délimitation des textes non fiables,
    tentative d'injection dans un nom d'instrument ;
  - présence des rappels obligatoires dans les consignes.
- **Revue manuelle** d'une vraie session de conseil dans Claude Desktop, consignée dans la spec.

---

## Phase 4 — Actu (news collectées, analyse à la demande)

- **ADR de la source de news** : flux RSS financiers ou API de news gratuite. Critères :
  conditions d'utilisation, droit de stockage, couverture de Paris, budget 0 €. Port
  `NewsProvider`.
- **Collecte et stockage par l'application, sans IA** :
  - job `@Scheduled`, sans ShedLock (une instance) ;
  - déduplication ;
  - rattachement aux valeurs par règles simples (ISIN, nom) ;
  - conservation bornée.
- **Page Actu** : liste des news, filtrée par valeur, date et source, avec lien vers la source.
  Le texte est affiché comme donnée, jamais par `innerHTML`.
- **Outil MCP `getLatestNews`** :
  - lecture seule, filtres par valeur et période, nombre de résultats borné ;
  - titres et extraits **délimités comme données non fiables**, avec source et date ;
  - consignes : une news n'est jamais une instruction.
- **Analyse lancée à la main** par le propriétaire depuis Claude Desktop (prompt MCP dédié).
  - De préférence dans une conversation où seul le connecteur InvestAI est activé : une news
    piégée ne doit pas pouvoir faire envoyer le portefeuille par un autre connecteur.
  - Pas d'agent planifié, pas de rapport généré automatiquement.
- Tests : adaptateur avec un faux flux, déduplication, E2E de la page Actu, sorties de l'outil
  (délimitation, injection de test dans un titre).

---

## Phase 5 — Simulation et notation des conseils (portefeuilles fictifs)

- **Domaine `SimulatedPortfolio`** :
  - capital initial fictif, par défaut la somme unique du profil ;
  - règles d'éligibilité PEA, frais paramétrables ;
  - ordres datés : dans le passé (backtest sur les cours historiques, limité par la profondeur
    d'historique) ou « à partir d'aujourd'hui » (suivi prospectif).
- Valorisation partagée avec le portefeuille déclaré (2.3), sans duplication de logique.
- **Outil MCP d'écriture `recordRecommendation`** ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md),
  section Écriture) : Claude enregistre la recommandation structurée.
  - Contenu : valeurs, poids ou montants, horizon, justification, risques, niveau de confiance,
    chiffres cités avec leur `asOf`.
  - **Confirmation explicite dans le client** : jamais « toujours autoriser ».
  - **Validation côté serveur** ; idempotent.
  - Statut **« proposée »** jusqu'à validation par le propriétaire dans l'interface d'InvestAI.
- **Chaque recommandation validée devient un portefeuille fictif suivi**, comparé à CW8 (MSCI
  World) et à l'ETF CAC 40 achetés au même moment avec le même montant : c'est la mesure objective
  de la valeur des conseils.
- **Notation** :
  - performance face aux deux ETF à 1, 3, 6 et 12 mois ;
  - pire baisse et volatilité comparées au profil de risque ;
  - tableau des notes.
- **Origine tracée** : chaque simulation sait si elle a été créée par le propriétaire, par Claude
  via MCP ou par le code applicatif, avec un lien vers la recommandation source.
- Outils MCP de lecture : `getSimulations`, `getSimulationPerformance`.
- Page Angular : liste, création, validation des recommandations proposées, détail avec la courbe
  de performance face aux benchmarks.

---

## Phase 6 — Qualité des conseils sans API

Remplace l'ancienne Phase 7 « Évaluations avancées ». Sans appel à l'API (ADR-0010), la CI ne
peut pas interroger le modèle : plus de LLM-as-judge, ni d'expériences Langfuse, ni de régression
mesurée sur les réponses. Ce qui reste mesurable :

- **Tests des outils et jeux de cas versionnés sur leurs sorties** : contrat de chaque outil,
  statuts, dates, limites rappelées, délimitation des données non fiables. Ils sont bloquants en
  CI dès qu'un outil ou une consigne change.
- **Grille de revue manuelle** d'une session de conseil, consignée dans le dépôt et repassée
  quand les consignes changent :
  - chiffres cités identiques à ceux des outils, avec leur `asOf` ;
  - profil respecté et diversification ;
  - limites des données et avertissement présents.
- **Notation des recommandations (Phase 5)** : performance des portefeuilles fictifs face à CW8
  et au CAC 40 dans le temps. C'est la mesure objective.

---

## Phase 7 — Durcissement

- Tests de charge légers (Gatling) sur le dashboard et le point `/mcp`.
- Revue sécurité complète : OWASP ASVS niveau 1, audit du modèle de menace IA (Phase 3) face au
  code réel, DAST (OWASP ZAP baseline en CI).
- Rate limiting de la connexion, gestion des pannes des fournisseurs de données (retry, circuit
  breaker Resilience4j).
- **Indicateur de plausibilité des cotations** (reporté le 2026-10-20 par le propriétaire, pas
  avant la fin de la Phase 3).
  - But : signaler une variation aberrante ou une division probable, qui place aujourd'hui une
    valeur en tête du tri par performance ([spec 2.4](specs/phase-2-04-indicators-dashboard.md),
    points ouverts).
  - En attendant, les outils MCP exposent le statut et les dates des indicateurs, et les consignes
    rappellent ce risque.

---

## Phase 8 — Synchronisation directe avec le courtier (optionnelle)

Non prioritaire, et peut-être jamais :
- la DSP2 n'impose l'accès qu'aux **comptes de paiement** : les PEA et comptes-titres n'en font pas partie, un agrégateur ne garantit donc pas l'accès aux positions ;
- le scraping de l'espace client est exclu (conditions d'utilisation, double authentification, fragilité, stockage des identifiants bancaires).

Le portefeuille déclaré (2.3) couvre le besoin. Si une API officielle apparaît : adaptateur derrière un port `BrokerSyncProvider`, le domaine ne change pas.

## Phase 9 — Déploiement cloud (à cadrer le moment venu)

- Images Docker du backend et du frontend (hadolint, Trivy), signées.
- Choix hébergeur via ADR, IaC, HTTPS, OIDC, sauvegardes Postgres, secrets managés, CD GitHub Actions avec environnements protégés.
- ShedLock sur les jobs planifiés (collecte des données de marché et des news) dès que plusieurs instances tournent.
- Serveur MCP joignable hors du poste : autorisation OAuth 2.1 prévue par la spécification MCP, en remplacement du jeton local (ADR-0010).

---

## Méthode de travail avec les coding agents (toutes phases)

1. Chaque feature commence par une **spec courte** (issue GitHub ou `docs/specs/`) + critères d'acceptation.
2. Plan mode → validation → implémentation **test-first** sur une branche dédiée.
3. `/check` local vert → PR → CI + review agent (`/code-review`, `/security-review`) → merge.
4. Toute décision structurante → ADR.
5. Mise à jour de `CLAUDE.md` dès qu'une erreur récurrente d'agent est constatée (les consignes évoluent avec le projet).

## Vérification

- **Phase 0** : PR pièges bloquées par chaque contrôle ✅.
- **Chaque phase suivante** : parcours E2E Playwright de la feature, CI verte, quality gate SonarCloud passé ; traces des appels d'outils visibles dans Grafana à partir de la Phase 3.
- **Phases IA (3 à 6)** :
  - tests des outils MCP et jeux de cas verts ;
  - revue manuelle d'une session de conseil consignée ;
  - budget IA 0 € : aucun appel à l'API d'un modèle ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md)).
