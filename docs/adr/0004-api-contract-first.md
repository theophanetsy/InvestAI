# 0004 — API REST documentée par OpenAPI, client Angular généré

- **Statut** : Accepté (révisé le 2026-10-14, Phase 1.4 : contrat versionné, génération par Maven)
- **Date** : 2026-10-13

## Contexte

Le frontend et le backend évoluent ensemble, souvent dans la même PR écrite par un agent. Un client
HTTP écrit à la main dérive silencieusement du backend.

## Options étudiées pour le générateur (prototypées en Phase 1.4)

- **openapi-generator `typescript-angular`** : l'outil standard du marché. Son code contient des
  imports inutilisés, refusés par `noUnusedLocals` de notre `tsconfig`.
- **ng-openapi-gen** : code compilant sans erreur avec nos options strictes, mais outil moins
  répandu.
- **@hey-api/openapi-ts** : client Angular encore jeune (version 0.x).

Pour lancer openapi-generator : le wrapper npm (`@openapitools/openapi-generator-cli`) tire NestJS,
axios, un script `postinstall` et télécharge le JAR à l'exécution sans le vérifier ; le **plugin
Maven** officiel est téléchargé et vérifié par Maven, suivi par Dependabot, et Java est déjà là
côté backend.

## Décision

- **Choix du propriétaire : l'outil standard, openapi-generator `typescript-angular`**, quitte à
  assouplir une règle tant que le niveau d'exigence reste le même sur notre code :
  `noUnusedLocals` / `noUnusedParameters` sont retirés du `tsconfig` (le code généré est compilé
  avec le reste) et remplacés, avec la même sévérité, par la règle ESLint
  `@typescript-eslint/no-unused-vars` (`args: all`) et
  `@typescript-eslint/no-unused-private-class-members` (membres `private`), qui s'appliquent à tout le code sauf
  `src/app/api/generated/`.
- `src/app/api/generated/**` est du code tiers, jamais édité, hors lint, hors couverture et hors
  analyse SonarCloud / CodeQL (`paths-ignore` dans `security.yml`) : les
  interdits du `CLAUDE.md` (`any`, directives qui ignorent une erreur TypeScript…) ne s'y
  appliquent pas. Ne pas le « corriger » ni le signaler en revue ; un problème se règle par la
  configuration du générateur.
- Le backend expose sa spécification OpenAPI 3.1 via **springdoc** (`/v3/api-docs`, session
  requise, limitée à `/api/**`). Le contrat est **versionné** dans `api/openapi.json` : chaque
  changement d'API est visible et relu dans la PR. `OpenApiContractIT` compare la spécification
  servie au fichier versionné et écrit la version courante dans `backend/target/openapi.json`.
- Le build backend génère le client (`openapi-generator-maven-plugin`, `target/angular-client`) à
  partir de `api/openapi.json` ; `AngularClientUpToDateTest` vérifie que
  `frontend/src/app/api/generated/` (fichiers `.ts`, jamais édités à la main) est identique. La CI
  frontend n'a donc pas besoin de Java.
- `npm run api:update` (racine) met à jour contrat et client en une commande.
- Conventions du contrat : préfixe `/api/v1`, JSON camelCase, dates ISO-8601 UTC, montants en
  chaîne décimale + devise, `operationId` = nom de la méthode Java (unique, vérifié), un tag par
  module (`@Tag`), champs `required` sauf `@Nullable` (et absents du JSON quand ils sont nuls),
  erreurs `application/problem+json` (schéma `Problem`, ADR-0006), serveur relatif.

## Conséquences

- ➕ Désalignement front/back détecté par la CI ; le contrat est un artefact lisible, base des
  futurs outils (MCP, Phase 6).
- ➕ Outil standard, mis à jour par Dependabot (écosystème Maven).
- ➖ Le contrôle « variable inutilisée » passe du compilateur à ESLint pour tout le frontend.
- ➖ Changer l'API demande `npm run api:update` après le build backend (le message d'échec le dit),
  avec Docker : le contrat est écrit par un test d'intégration.
- ➖ Le build backend dépend de `api/` et de `frontend/` (contexte Docker = racine du dépôt).
- ➖ Le frontend doit fixer le chemin de base à `''` (`provideApiClient()`), sinon le client
  généré appelle `http://localhost`.
