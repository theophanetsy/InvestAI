# Phase 1.3 — Authentification mono-utilisateur

## Contexte

Avant toute donnée de portefeuille (Phase 2), l'API doit être fermée par défaut. Décision :
[ADR-0007](../adr/0007-authentification-mono-utilisateur.md). Le contrat d'erreur est celui de
l'[ADR-0006](../adr/0006-contrat-erreurs-et-logs.md) (Phase 1.2).

## Critères d'acceptation

1. **Fermé par défaut** — **Given** un client anonyme, **when** il appelle un endpoint qui n'est
   pas explicitement public, **then** 401 problem+json `code = "auth.required"` avec `requestId`.
   Publics : `GET /api/v1/status`, `/actuator/health/liveness`, `/actuator/health/readiness`,
   `POST /api/v1/auth/login`, `GET /api/v1/auth/me` (401 si anonyme).
2. **Connexion** — **Given** les bons identifiants, **when** `POST /api/v1/auth/login`
   `{ "username", "password" }`, **then** 204, un cookie de session `HttpOnly` +
   `SameSite=Strict`, un **nouvel** identifiant de session (anti-fixation) ; `GET /api/v1/auth/me`
   renvoie alors `{ "username" }`.
3. **Échec de connexion** — **Given** un mauvais mot de passe **ou** un utilisateur inconnu,
   **then** la même réponse 401 `auth.invalid-credentials` (pas d'énumération), et un log
   `WARN` sans le mot de passe ni le nom saisi.
4. **Déconnexion** — **Given** une session, **when** `POST /api/v1/auth/logout`, **then** 204,
   session invalidée, cookie supprimé ; la requête suivante est anonyme.
5. **CSRF** — **Given** une requête modifiante (`POST`, `PUT`, `PATCH`, `DELETE`) sans en-tête
   `X-XSRF-TOKEN` valide, **then** 403 `auth.csrf-invalid` (y compris sur `login`). Le jeton est
   fourni dans le cookie `XSRF-TOKEN` lisible par Angular (`HttpClient` l'envoie seul).
6. **Erreurs hors Spring MVC** — 401/403 émis par les filtres de sécurité, URL rejetée par le
   pare-feu HTTP de Spring Security, et toute réponse passant par `/error` : problem+json au
   contrat ADR-0006 (`code`, `requestId`, jamais de `detail`). `ApiExceptionHandler` relance les
   exceptions de sécurité levées dans les contrôleurs au lieu d'en faire des 500.
7. **Configuration** — l'utilisateur vient de `INVESTAI_USERNAME` / `INVESTAI_PASSWORD_HASH` ;
   l'application **refuse de démarrer** si l'un manque ou si le hash n'a pas le format
   `{id}…`. `scripts/hash-password.mjs` produit un hash accepté par Spring Security (vérifié par
   un test avec un vecteur généré par le script).
8. **Santé** — la sonde `readiness` reste publique mais n'expose le détail des composants (`db`)
   qu'à un utilisateur connecté (`show-components=when-authorized`).
9. **En-têtes** — réponses API avec `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`,
   `X-Frame-Options: DENY` et `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`.

## Hors périmètre

- Écran de connexion et intercepteur 401 côté Angular : Phase 1.5 (shell).
- Limitation des tentatives de connexion : Phase 8.
- OIDC / multi-utilisateur : Phase 10.
- Agent de vérification sur l'app lancée (Playwright exploratoire) : quand l'UI existera (1.5).
