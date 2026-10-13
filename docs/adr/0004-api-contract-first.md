# 0004 — API REST documentée par OpenAPI, client Angular généré

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

Le frontend et le backend évoluent ensemble, souvent dans la même PR écrite par un agent. Un client
HTTP écrit à la main dérive silencieusement du backend.

## Décision

- Le backend expose sa spécification OpenAPI via springdoc (Phase 1), exportée dans
  `backend/target/openapi.json` pendant le build.
- Le client TypeScript est généré (openapi-generator, `typescript-angular`) dans
  `frontend/src/app/api/generated/`, jamais édité à la main.
- La CI vérifie que le client généré est à jour (régénération + `git diff --exit-code`).
- Conventions : préfixe `/api/v1`, JSON camelCase, dates ISO-8601 UTC, montants en chaîne
  décimale + devise, erreurs au format ProblemDetail (RFC 9457).

## Conséquences

- ➕ Désalignement front/back détecté à la compilation.
- ➖ Une étape de génération à intégrer au build (Phase 1).
