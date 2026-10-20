# Phase 1.6 — Agent `app-verifier`

## Contexte

Les tests e2e (Playwright) tournent contre une API simulée (`page.route`) : ils ne voient ni le
vrai backend, ni la base, ni les contrats réels (CSRF, cookies, erreurs). Les relectures de la
Phase 1.5 l'ont montré : la nouvelle tentative CSRF passait les tests mais échouait contre le vrai
comportement de Spring. Cette étape donne aux agents un moyen de **vérifier un changement dans
l'application réelle**, comme le ferait l'utilisateur, avant d'ouvrir la PR.

## Critères d'acceptation

1. **Stack isolée en une commande** — **Given** Docker démarré, **when** on lance
   `npm run stack:start` (racine), **then** une base PostgreSQL jetable, le backend et le frontend
   démarrent, et la commande se termine en affichant l'URL (`http://localhost:14200`),
   l'identifiant et l'emplacement du mot de passe (`.local-stack/state.json`, jamais affiché :
   règle CodeQL `js/clear-text-logging`) une fois l'application prête (readiness backend `UP`,
   frontend servi).
2. **Isolation** — la stack de vérification n'utilise ni les ports (5432, 8080, 4200), ni la
   base, ni les identifiants de la stack de développement : projet Compose `investai-verify`,
   ports 15432 / 18080 / 14200, mot de passe de base et de connexion aléatoires à chaque
   démarrage, `infra/.env` jamais lu par le script. La base est recréée vide à chaque démarrage.
3. **Arrêt complet** — `npm run stack:stop` arrête backend et frontend (arbre de processus) et
   supprime la base jetable (`down -v`) ; `npm run stack:status` affiche l'état. Un port occupé ou
   Docker absent donne un message clair, sans processus orphelin.
4. **Agent** — `.claude/agents/app-verifier.md` : démarre la stack, rejoue les critères
   d'acceptation dans un navigateur (serveur MCP `investai-browser` : Playwright MCP en version
   figée, sans interface, profil en mémoire, requêtes restreintes à `http://localhost:14200`),
   contrôle console, réseau, accessibilité et affichage mobile, arrête toujours la stack, rend un
   rapport par critère. Il ne modifie aucun fichier et n'utilise que le compte jetable.
5. **Commande** — `/verify-app <critères ou spec>` délègue à l'agent ; le workflow de `CLAUDE.md`
   l'exige pour un changement visible dans l'UI (sinon le signaler dans la PR).
6. **Recette** — l'agent vérifie le shell de la Phase 1.5 contre la vraie application : connexion,
   navigation, déconnexion puis reconnexion (CSRF réel), page introuvable, mobile.

## Hors périmètre

- Exécution en CI (la vérification par agent est locale ; la CI garde les e2e simulés).
- Image Docker de l'application (Phase 9) : backend et frontend tournent depuis les sources.
