# Phase 3.0 — Squelette du serveur MCP

## Contexte

[ADR-0010](../adr/0010-ia-via-mcp-uniquement.md), accepté par le propriétaire le 2026-10-20 :
l'IA passe **uniquement** par un serveur MCP intégré au backend, que le propriétaire branche dans
Claude Code ou Claude Desktop (son abonnement). L'application n'appelle jamais l'API Anthropic.

Cette étape pose le **squelette** : la dépendance, le point `/mcp` et sa sécurité, un seul outil
(santé) pour valider le branchement de bout en bout, la passerelle de Claude Desktop, le modèle de
menace et la documentation. Les outils métier et les consignes de conseil arrivent en 3.1.

## Critères d'acceptation

### 1. Dépendance et architecture

1. Dépendance **`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`**, version exacte
   épinglée dans `backend/pom.xml`, vérifiée le jour de l'ajout :
   - dernière version stable sur Maven Central, construite sur la version de Spring Boot du
     backend ;
   - aucune vulnérabilité connue (base OSV) pour elle et ses dépendances transitives nouvelles ;
   - **`spring-webflux` absent** du classpath (vérifié par l'arbre des dépendances).
2. Seul `..infrastructure..` dépend de la bibliothèque MCP (`io.modelcontextprotocol..`,
   `org.springframework.ai..`) : règle ArchUnit, prouvée par une fixture fautive. Le domaine reste
   sans bibliothèque MCP.
3. Nouveau module **`assistant`** (`domain`, `application`, `infrastructure`) : il appelle les cas
   d'usage des autres modules par leur package `application`, **jamais leurs dépôts** (ports
   `*Repository`), ni leur `infrastructure` ou leur `api` : règle ArchUnit prouvée par une fixture.

### 2. Transport

4. Transport **Streamable HTTP sans état** (`STATELESS`, API synchrone) sur **`/mcp`**, dans le
   backend. En local (profil `local`), le backend n'écoute que sur `127.0.0.1` (déjà le cas).
5. Le serveur se présente comme `investai` avec la version de l'application ; il n'annonce que la
   capacité **outils** (ni ressources, ni prompts, ni complétions en 3.0).
6. **Liste blanche** : les outils sont déclarés explicitement dans une seule configuration
   (`assistant.infrastructure`) ; ni balayage automatique des annotations, ni conversion
   automatique des `ToolCallback` de Spring AI.

### 3. Sécurité de `/mcp`

7. **Activation par le jeton** : sans `INVESTAI_MCP_TOKEN_HASH` (vide ou absente), `/mcp` **n'est
   pas monté** (ni route, ni serveur MCP, ni chaîne de sécurité dédiée) et **un avertissement**
   est écrit au démarrage. Le reste de l'application fonctionne normalement.
8. **Jeton porteur dédié** (`Authorization: Bearer <jeton>`) :
   - jeton de 256 bits aléatoires, préfixé `investai_mcp_` (reconnaissable par les scanners de
     secrets), produit par `scripts/mcp-token.mjs` ;
   - le backend ne connaît que son **empreinte SHA-256** en hexadécimal (`INVESTAI_MCP_TOKEN_HASH`),
     comparée en **temps constant** ;
   - une empreinte mal formée (ou le jeton collé à la place de l'empreinte) **empêche le
     démarrage**, avec un message qui ne contient jamais la valeur ;
   - le script n'affiche jamais le jeton : il l'écrit dans un fichier **hors du dépôt**
     (`~/.investai/mcp-token` par défaut, droits restreints, jamais écrasé sans `--force`) et
     affiche l'empreinte et le chemin du fichier.
9. Réponses de `/mcp` (problem+json de l'ADR-0006, avec `requestId`) :
   - sans jeton, ou jeton faux : **401** `auth.required` (même réponse dans les deux cas) ;
   - requête portant un en-tête **`Origin`** (quelle que soit sa valeur, jeton valide ou non) :
     **403** `auth.forbidden` ;
   - `Host` inattendu : **400** (pare-feu existant, ADR-0007, inchangé) ;
   - **cookie de session refusé** : une session valide sans jeton donne 401 ; `/mcp` ne lit ni ne
     crée jamais de session (aucun `Set-Cookie`) ; pas de CSRF (aucune authentification par
     cookie) ;
   - le jeton MCP **n'ouvre pas `/api/v1`** (401).
10. **Limite de débit** sur `/mcp` : 120 requêtes par minute glissante (configurable), **429**
    `mcp.rate-limited` avec `Retry-After`. Deux budgets séparés, requêtes authentifiées et
    requêtes refusées, pour qu'un processus local sans jeton ne puisse pas épuiser le budget du
    client légitime.
11. **Journalisation** : une ligne par appel d'outil avec le nom de l'outil, le statut, la durée,
    la taille du résultat et le `requestId` (MDC). Jamais les arguments, jamais le résultat.
    **Le jeton n'apparaît jamais** dans un log ni dans une réponse d'erreur (test sur la sortie
    capturée). Une erreur dans un outil renvoie au client un résultat d'erreur générique
    (`tool.failed` + `requestId`), sans message d'exception.

### 4. Outil de santé

12. Un seul outil, **`getHealth`**, sans argument, en lecture seule (`readOnlyHint: true`,
    `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`), avec un schéma de
    sortie et un contenu structuré :
    - `version` de l'application ;
    - `closesAsOf` : séance de la dernière clôture en base (absente si aucune) ;
    - `indicatorsAsOf` et `indicatorsComputedAt` : séance de référence et horodatage du dernier
      calcul des indicateurs (absents s'il n'a jamais tourné) ;
    - `status` : `READY` (clôtures et indicateurs), `NO_CLOSES`, `INDICATORS_NOT_COMPUTED`.
    Il lit la base par les cas d'usage de `market` (aucun appel externe).
13. La liste renvoyée par `tools/list` est **figée par un test** (noms, annotations, schéma
    d'entrée sans argument).

### 5. Passerelle stdio pour Claude Desktop

14. `scripts/mcp-stdio-gateway.mjs`, **sans dépendance** (Node seul), sans `shell: true` :
    - lit les messages JSON-RPC (un par ligne) sur l'entrée standard et relaie chacun par un
      `POST` vers `INVESTAI_MCP_URL` (défaut `http://127.0.0.1:8080/mcp`) ; écrit les réponses
      sur la sortie standard, un message par ligne ; les notifications (202) ne produisent rien ;
    - jeton lu dans `INVESTAI_MCP_TOKEN` ou dans le fichier `INVESTAI_MCP_TOKEN_FILE` (défaut
      `~/.investai/mcp-token`), **jamais en argument** de ligne de commande ;
    - refuse une URL qui n'est pas en HTTP sur une adresse de bouclage (le jeton ne part jamais
      ailleurs) ;
    - une erreur HTTP ou réseau devient une erreur JSON-RPC pour la requête concernée, avec le
      statut HTTP et le code du problème, jamais le jeton ; diagnostics sur la sortie d'erreur.
15. Testée automatiquement : relais réel `initialize`, `tools/list`, `tools/call` contre le
    backend de test (intégration JUnit, exécutée en CI), et cas d'erreur en `node --test`.

### 6. Documentation et modèle de menace

16. `docs/setup.md` : générer le jeton, renseigner `INVESTAI_MCP_TOKEN_HASH` dans `infra/.env`,
    brancher **Claude Code en portée utilisateur** (`claude mcp add --transport http --scope
    user …`, **jamais dans le `.mcp.json` du dépôt**), brancher **Claude Desktop** par la
    passerelle (`claude_desktop_config.json`), règles d'usage de l'ADR-0010.
17. Modèle de menace IA `docs/security/ai-threat-model.md` (OWASP Top 10 LLM 2025) : données non
    fiables (noms d'instruments, texte libre du profil, news), exfiltration par les autres
    connecteurs du client, futur outil d'écriture (Phase 5), et ce que chaque parade couvre ou
    non.
18. La stack de vérification (`npm run stack:start`) monte `/mcp` avec un **jeton jetable**,
    rangé dans `.local-stack/state.json` (ignoré par Git), jamais affiché ; elle ignore toujours
    l'empreinte de `infra/.env`.

### 7. Nettoyage de l'ancienne numérotation

19. Commentaires et pages qui citent l'ancienne numérotation ou le chat (ROADMAP, « Renumérotation ») :
    ShedLock « phase 10 » → Phase 9 dans `MarketJobs` ; page Assistant (plus de chat : le conseil
    se fait dans Claude via MCP), Actu → Phase 4, Simulation → Phase 5 ; tests à jour.

## Hors périmètre

- **Observabilité (Micrometer, OpenTelemetry, Grafana LGTM)** : sortie en **3.0b** (voir
  « Écarts »).
- Outils métier, instructions du serveur, prompt `conseil-placement` : 3.1.
- OAuth 2.1 : avant toute exposition hors du poste (Phase 9).

## Écarts et précisions d'implémentation

- **Observabilité reportée en 3.0b** : elle ajoute plusieurs dépendances (pont Micrometer
  Tracing, exporteur OTLP), un conteneur, et surtout le **masquage de la clé EODHD** dans les
  spans du client HTTP du JDK (piège connu de `backend/CLAUDE.md`, test sur un span exporté). La
  faire à moitié dans cette PR serait risqué ; elle a sa propre étape dans la ROADMAP.
- **Activation** : le starter Spring AI s'active par `spring.ai.mcp.server.enabled`. Une
  propriété ne pouvant pas dépendre du fait qu'une autre soit vide, un `EnvironmentPostProcessor`
  (`auth.infrastructure.McpEndpointActivation`) fixe cette propriété d'après
  `INVESTAI_MCP_TOKEN_HASH` : le jeton est **le seul interrupteur**.
- **La sécurité de `/mcp` vit dans `auth.infrastructure`** (comme celle de l'API, ADR-0007) ; le
  module `assistant` ne contient que l'outil, sa journalisation et le cas d'usage.
