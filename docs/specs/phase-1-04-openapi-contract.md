# Phase 1.4 — Contrat OpenAPI et client Angular généré

## Contexte

Le frontend appelle aujourd'hui l'API avec un client écrit à la main (`SystemStatusService`,
interface `SystemStatus`), qui dérivera du backend sans que rien ne le signale. Décision :
[ADR-0004](../adr/0004-api-contract-first.md) (révisée pour cette étape). Le contrat d'erreur est
celui de l'[ADR-0006](../adr/0006-contrat-erreurs-et-logs.md), l'authentification celle de
l'[ADR-0007](../adr/0007-authentification-mono-utilisateur.md).

## Critères d'acceptation

1. **Contrat versionné** — **Given** le backend, **when** `./mvnw verify` s'exécute, **then** la
   spécification OpenAPI 3.1 servie par springdoc est écrite dans `backend/target/openapi.json`
   et comparée au fichier versionné `api/openapi.json` ; toute différence fait échouer le build
   avec la commande à lancer (`npm run api:update` depuis la racine).
2. **Client à jour** — **Given** `api/openapi.json`, **when** `./mvnw verify` s'exécute,
   **then** openapi-generator (`typescript-angular`, plugin Maven) génère le client dans
   `backend/target/angular-client` et le compare aux fichiers `.ts` de
   `frontend/src/app/api/generated/` ; toute différence fait échouer le build. Aucune
   modification de workflow CI, pas de Java dans la CI frontend.
3. **Client utilisé** — le frontend n'a plus de type ni d'appel HTTP écrits à la main pour l'API :
   l'écran d'accueil appelle le service généré (`SystemService.getStatus()`, typé par le modèle
   généré `StatusResponse`). Les requêtes partent vers la même origine (`/api/v1/status`, jamais
   `http://localhost/…` ni `//api/…`), ce que vérifie l'e2e.
4. **Contrat fidèle**
   - Seul `/api/**` est décrit. Chaque opération a un `operationId` lisible et unique (nom de la
     méthode Java, pas de suffixe `_1`) et un tag de module (`system`, `auth`) : un service
     généré par module. Les réponses sont en `application/json`.
   - Les champs des DTO sont `required` sauf s'ils sont annotés `@Nullable` (JSpecify), comme le
     contrat de nullité Java (NullAway) : pas de `?` superflu côté TypeScript. Un champ nul est
     absent du JSON (`spring.jackson.default-property-inclusion=non_null`).
   - Le schéma `Problem` (RFC 9457 + `code`, `requestId`, `errors[{ field, code }]`) est décrit et
     chaque opération déclare une réponse d'erreur `application/problem+json` qui le référence.
   - `POST /api/v1/auth/logout`, traité par un filtre Spring Security, figure dans le contrat.
   - Le serveur est relatif (même origine que le frontend) : pas d'URL `http://localhost:<port>`.
5. **Accès** — `GET /v3/api-docs` exige une session (API fermée par défaut, ADR-0007). Pas de
   Swagger UI : sa CSP serait incompatible avec `default-src 'none'`.
6. **Mise à jour en une commande** — `npm run api:update` (racine) copie le contrat produit par le
   dernier build backend dans `api/openapi.json` puis régénère le client.
7. **Règles TypeScript** (décision du propriétaire, ADR-0004) — le code généré compile avec le
   reste ; `noUnusedLocals` / `noUnusedParameters` sont remplacés par
   `@typescript-eslint/no-unused-vars` (`args: all`) et
   `@typescript-eslint/no-unused-private-class-members` pour tout le code hors `api/generated`.

## Hors périmètre

- Swagger UI ou autre visualiseur intégré (le fichier `api/openapi.json` se lit dans l'IDE).
- Intercepteur d'erreurs et écran de connexion côté Angular : Phase 1.5.
- Détection des changements cassants entre versions du contrat (oasdiff) : quand l'API aura des
  consommateurs externes (Phase 6, MCP).
