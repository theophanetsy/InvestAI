# Phase 1.5 — Shell Angular

## Contexte

Le frontend n'affiche aujourd'hui qu'un en-tête avec l'état du backend. Cette étape pose la
structure dans laquelle viendront les features (dashboard, chat, actu, simulation) : mise en page,
navigation, connexion et gestion des erreurs HTTP. Bibliothèque UI :
[ADR-0008](../adr/0008-bibliotheque-ui-angular-material.md). Contrats consommés : erreurs
[ADR-0006](../adr/0006-contrat-erreurs-et-logs.md), authentification
[ADR-0007](../adr/0007-authentification-mono-utilisateur.md), client généré
[ADR-0004](../adr/0004-api-contract-first.md).

## Critères d'acceptation

1. **Mise en page** — **Given** un utilisateur connecté, **then** chaque page affiche une barre
   d'application (nom de l'app, état du backend, nom de l'utilisateur, bouton « Se déconnecter »)
   et une navigation vers Tableau de bord, Assistant, Actu et Simulation. La page active est
   signalée (`aria-current="page"`). Sur écran étroit, la navigation se replie derrière un bouton
   « Menu », la barre ne garde que le menu, le nom de l'app et « Se déconnecter » (pas de
   défilement horizontal).
2. **Routing lazy** — chaque feature est un composant chargé à la demande (`loadComponent`, un
   chunk par feature) ; chaque page a un titre de document (`<titre> · InvestAI`). `/` redirige
   vers `/dashboard` ; une URL inconnue affiche une page « Page introuvable » avec un lien vers le
   tableau de bord.
3. **Accès protégé** — **Given** aucune session, **when** l'utilisateur ouvre une page de l'app
   (ex. `/simulation`), **then** il est redirigé vers `/login?returnUrl=%2Fsimulation`, sans que le
   code de la feature soit affiché. **Given** une session, **when** il ouvre `/login`, **then** il
   est redirigé vers le tableau de bord. L'état de session vient de `GET /api/v1/auth/me`, appelé
   une seule fois au démarrage (vérifications simultanées partagées) ; si le serveur est
   injoignable, la navigation suivante redemande.
4. **Connexion** — le formulaire (identifiant, mot de passe, champs obligatoires, labels
   accessibles) appelle `AuthService.login` puis `me`, puis navigue vers `returnUrl` s'il s'agit
   d'un chemin interne (commence par `/`, sans `//`, `\` ni caractère de contrôle), sinon vers
   `/dashboard`. Un 401 `auth.invalid-credentials` affiche « Identifiant ou mot de passe
   incorrect » ; toute autre erreur un message générique avec la référence (`requestId`). Le
   bouton est désactivé pendant l'envoi.
5. **Déconnexion** — « Se déconnecter » (désactivé pendant l'envoi) appelle `AuthService.logout`,
   oublie l'utilisateur et affiche l'écran de connexion ; un 401 (session déjà finie) vaut succès.
   Tout autre échec garde l'utilisateur et affiche « La déconnexion a échoué » avec la référence.
6. **Session expirée** — **Given** une session expirée, **when** un appel API renvoie 401,
   **then** l'utilisateur est renvoyé vers `/login` avec la page courante en `returnUrl`. Les appels
   d'authentification (`me`, `login`, `logout`) sont exclus de cette règle (ils traitent leur 401
   eux-mêmes).
7. **Jeton CSRF invalide** — un 403 `auth.csrf-invalid` déclenche une seule nouvelle tentative,
   après un `GET` qui renouvelle le cookie `XSRF-TOKEN`, avec le nouveau jeton dans
   `X-XSRF-TOKEN`. Cas réel : se reconnecter juste après une déconnexion (qui supprime le cookie).
8. **Erreurs serveur** — une erreur réseau (statut 0) ou ≥ 500 affiche une notification
   (« Serveur injoignable… » / « Erreur du serveur… Référence : `<requestId>` »). Les erreurs 4xx
   restent traitées par l'écran qui a fait l'appel. Un appel peut se retirer de la notification
   (indicateur d'état du backend, qui affiche déjà l'erreur).
9. **Erreur typée** — une `HttpErrorResponse` est convertie en `Problem` (modèle généré) : le corps
   problem+json s'il est valide, sinon un `Problem` construit avec `code` `network.unavailable`
   (statut 0) ou `http.<statut>`, et le `requestId` de l'en-tête `X-Request-Id` s'il existe.
10. **Thème** — Angular Material 22 (thème M3, clair ou sombre selon le système), polices système
   (aucune requête vers un CDN de polices).

## Hors périmètre

- Contenu des features (Phases 2 à 5) : chaque page affiche son titre et « Bientôt disponible ».
- Agent `app-verifier` (stack locale + exploration Playwright) : PR suivante.
- Internationalisation et locale `fr` des pipes (montants, dates) : Phase 2, avec le dashboard.
- Service du frontend en production (image, CSP du HTML) : avec le `docker compose` complet.
