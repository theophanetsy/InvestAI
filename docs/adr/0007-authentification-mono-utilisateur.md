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

- **Session serveur + cookie `HttpOnly`, `SameSite=Strict`**, protection CSRF par double
  soumission : cookie `XSRF-TOKEN` lisible par Angular, jeton accepté **uniquement** dans
  l'en-tête `X-XSRF-TOKEN`, jamais en paramètre `_csrf` (les cookies ne sont pas isolés par
  port : une page servie sur un autre `localhost:*` peut lire le cookie et poster un formulaire,
  mais pas ajouter d'en-tête à une requête cross-origin). Points d'entrée JSON : `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`,
  `GET /api/v1/auth/me`. Changement d'identifiant de session à la connexion (fixation).
- **Un seul utilisateur**, défini par configuration : `INVESTAI_USERNAME` et
  `INVESTAI_PASSWORD_HASH` (variables d'environnement, ou `infra/.env` en profil `local`).
  Jamais de mot de passe en clair : le hash est au format `DelegatingPasswordEncoder`
  (`{pbkdf2@SpringSecurity_v5_8}…`, PBKDF2-HMAC-SHA256, JDK uniquement, sans dépendance),
  produit par `scripts/hash-password.mjs`. Démarrage refusé si la configuration manque ou si le
  hash a un autre format : les encodages faibles acceptés par défaut par Spring Security
  (`{noop}`, `{MD5}`…) sont refusés.
- **Erreurs** au contrat de l'ADR-0006 : 401 `auth.required` / `auth.invalid-credentials`,
  403 `auth.forbidden` / `auth.csrf-invalid`, avec `requestId` — y compris pour les erreurs émises
  hors de Spring MVC (filtres, `/error`).
- **Exposition** : en local, le backend n'écoute que sur `127.0.0.1` ; les requêtes dont l'en-tête
  `Host` n'est pas attendu (`investai.security.allowed-hostnames`) sont rejetées par le pare-feu
  HTTP de Spring Security (protection contre le DNS rebinding).
- Publics : `GET /api/v1/status`, sondes `/actuator/health/{liveness,readiness}` (sans détail des
  composants pour un anonyme), `POST /api/v1/auth/login`. Tout le reste exige une session.

## Conséquences

- ➕ Aucun secret dans le dépôt ; passage à OIDC en Phase 10 sans toucher aux contrôleurs (seule
  la configuration de sécurité change).
- ➕ Contrat d'erreur homogène, testable sans navigateur.
- ➖ Session en mémoire : perdue au redémarrage (acceptable en local ; Spring Session JDBC si
  besoin plus tard).
- ➖ Pas de limitation des tentatives de connexion avant la Phase 8 (rate limiting) ; risque faible
  tant que l'app n'écoute que sur `127.0.0.1` et filtre l'en-tête `Host`.
- ➖ Risques acceptés pour un poste local mono-utilisateur, à revoir en Phase 8 / 10 :
  - PBKDF2 à 310 000 itérations (défaut Spring Security) au lieu des 600 000 recommandées par
    l'OWASP : ne joue qu'en cas de fuite de `infra/.env` (attaque hors ligne) ; mot de passe
    de 12 caractères minimum imposé par le script.
  - Pas de contrôle contre les mots de passe compromis (ASVS 2.1.7), pas de durée de session
    absolue (expiration après 30 min d'inactivité, défaut du conteneur).
  - Nom d'utilisateur comparé sans tenir compte de la casse (`InMemoryUserDetailsManager`).
  - Cookie `XSRF-TOKEN` marqué `Secure` seulement si la requête est en HTTPS : derrière un
    reverse proxy TLS, configurer `server.forward-headers-strategy` (Phase 10).
