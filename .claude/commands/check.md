---
description: Lance toute la chaîne qualité locale (équivalent CI) et corrige les erreurs
---

Lance les vérifications complètes du projet et corrige ce qui échoue.

1. Détermine les modules touchés avec `git status` et `git diff --stat main...HEAD`.
2. Backend touché → `cd backend && ./mvnw -B verify`.
3. Frontend touché → `cd frontend && npm run check`, puis `npm run e2e` si des composants/routes ont changé.
4. Lance `gitleaks git --redact --no-banner -v .` pour vérifier l'absence de secrets.
5. Pour chaque échec : lis le rapport (`backend/target/*.xml`, sortie console), corrige **la cause**
   dans le code. Ne désactive jamais une règle et n'abaisse aucun seuil (voir `CLAUDE.md`).
6. Relance jusqu'à ce que tout soit vert, puis résume : contrôles passés, corrections faites.

$ARGUMENTS
