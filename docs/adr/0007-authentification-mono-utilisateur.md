# 0007 — Authentification mono-utilisateur : session, CSRF, identifiants hors du code

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

InvestAI tourne en local pour un seul utilisateur, mais manipule des données patrimoniales et
exposera un agent IA capable d'agir (Phase 3+). L'API doit donc être protégée dès maintenant, sans
dépendre d'un fournisseur d'identité (OIDC prévu au déploiement cloud, Phase 10).

Le frontend Angular est servi sur la **même origine** que l'API (proxy `/api` en développement,
reverse proxy en production) : pas de CORS.

## Options étudiées

- **Session (cookie `HttpOnly`) + CSRF** : standard Spring Security ; le cookie n'est pas lisible
  par JavaScript (une XSS ne vole pas la session) ; CSRF géré nativement par Spring Security
  (cookie `XSRF-TOKEN`) et Angular `HttpClient` (en-tête `X-XSRF-TOKEN`) ; déconnexion réelle.
- **JWT stocké côté navigateur** : sans état, mais le jeton est lisible par JavaScript
  (`localStorage`) ou impose les mêmes mécanismes cookie + CSRF ; révocation compliquée. Utile
  entre services, pas pour un SPA sur la même origine.
- **HTTP Basic** : trivial, mais identifiants envoyés à chaque requête, pas de déconnexion,
  popup navigateur.
- **OIDC dès maintenant** (Keycloak dans le compose) : la bonne cible en cloud, mais
  disproportionné pour un poste local mono-utilisateur.

## Décision

- **Session serveur + cookie `HttpOnly`, `SameSite=Strict`**, protection CSRF en mode SPA de
  Spring Security. Points d'entrée JSON : `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`,
  `GET /api/v1/auth/me`. Changement d'identifiant de session à la connexion (fixation).
- **Un seul utilisateur**, défini par configuration : `INVESTAI_USERNAME` et
  `INVESTAI_PASSWORD_HASH` (variables d'environnement, ou `infra/.env` en profil `local`).
  Jamais de mot de passe en clair : le hash est au format `DelegatingPasswordEncoder`
  (`{pbkdf2@SpringSecurity_v5_8}…`, PBKDF2-HMAC-SHA256, JDK uniquement, sans dépendance),
  produit par `scripts/hash-password.mjs`. Démarrage refusé si la configuration manque.
- **Erreurs** au contrat de l'ADR-0006 : 401 `auth.required` / `auth.invalid-credentials`,
  403 `auth.forbidden` / `auth.csrf-invalid`, avec `requestId` — y compris pour les erreurs émises
  hors de Spring MVC (filtres, `/error`).
- Publics : `GET /api/v1/status`, sondes `/actuator/health/{liveness,readiness}` (sans détail des
  composants pour un anonyme), endpoints d'authentification. Tout le reste exige une session.

## Conséquences

- ➕ Aucun secret dans le dépôt ; passage à OIDC en Phase 10 sans toucher aux contrôleurs (seule
  la configuration de sécurité change).
- ➕ Contrat d'erreur homogène, testable sans navigateur.
- ➖ Session en mémoire : perdue au redémarrage (acceptable en local ; Spring Session JDBC si
  besoin plus tard).
- ➖ Pas de limitation des tentatives de connexion avant la Phase 8 (rate limiting) ; risque faible
  tant que l'app n'écoute que sur `localhost`.
