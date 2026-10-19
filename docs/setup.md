# Installation du poste de dev

## Prérequis

| Outil | Version | Installation (Windows) |
|---|---|---|
| JDK Temurin | 25 | `winget install EclipseAdoptium.Temurin.25.JDK` puis définir `JAVA_HOME` |
| Node.js | 24 (`.nvmrc`) | https://nodejs.org ou `nvm` |
| gitleaks | ≥ 8.30 | `winget install Gitleaks.Gitleaks` (requis par le hook pre-commit) |
| Docker Desktop | récent | **obligatoire** : PostgreSQL local et tests d'intégration (Testcontainers) |
| GitHub CLI | récent | `winget install GitHub.cli` |

Maven n'a pas besoin d'être installé : utiliser `backend/mvnw`.

## Premier lancement

```bash
npm install                 # installe Lefthook + commitlint et active les git hooks
cd frontend && npm ci && npx playwright install chromium
cd ../backend && ./mvnw verify   # Docker doit tourner (Testcontainers)
```

## Lancer l'application en local

```bash
cp infra/.env.example infra/.env     # puis renseigner les variables (fichier ignoré par Git)
node scripts/hash-password.mjs      # → valeur de INVESTAI_PASSWORD_HASH dans infra/.env
cd backend && ./mvnw spring-boot:run # profil local : démarre PostgreSQL via Docker Compose
cd frontend && npm start             # http://localhost:4200 (proxy /api → :8080)
```

- `infra/.env` contient `POSTGRES_PASSWORD`, `INVESTAI_USERNAME` et `INVESTAI_PASSWORD_HASH`
  (hash PBKDF2, jamais le mot de passe en clair ; [ADR-0007](adr/0007-authentification-mono-utilisateur.md)).
  Sans eux, le backend refuse de démarrer. Il contient aussi `INVESTAI_EODHD_API_KEY`, facultative
  (données de marché, voir plus bas).
- Ouvrir http://localhost:4200 : l'écran de connexion demande `INVESTAI_USERNAME` et le mot de
  passe dont le hash est dans `INVESTAI_PASSWORD_HASH`.
- Santé : http://localhost:8080/actuator/health/readiness (détail de la base une fois connecté).

### Données de marché (EODHD, Euronext)

Le backend collecte seul les données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md),
[spec 2.1](specs/phase-2-01-market-data-collection.md)) :

- **Clôtures quotidiennes** : fichiers différés gratuits d'Euronext, sans clé. Job toutes les
  heures de 9 h à 18 h (heure de Paris), séance précédente, un seul téléchargement réussi par
  fichier et par séance.
- **Référentiel, 1 an d'historique, rattrapage des jours manqués** : offre gratuite d'EODHD.
  Créer un compte sur https://eodhd.com/register, copier la clé d'API du tableau de bord et
  l'ajouter à `infra/.env` : `INVESTAI_EODHD_API_KEY=<clé>` (jamais ailleurs, jamais commitée).
  Job au démarrage (après 30 s) puis toutes les heures ; budget de 15 appels par jour UTC
  (quota gratuit : 20), consommation visible dans les logs (`calls=3/15`).
- **Sans clé** : l'application démarre, la collecte Euronext tourne, les fonctions EODHD sont
  désactivées (un seul avertissement au démarrage). Sans référentiel, aucune valeur n'est suivie :
  la clé est nécessaire pour un usage réel.
- Premier démarrage avec la clé : référentiel de Paris (1 appel), suivi automatique des
  benchmarks (CAC, CW8, WPEA) et de leur historique (3 appels). Le lendemain matin, la collecte
  ajoute la clôture de la veille.
- Couper les jobs (dépannage) : `INVESTAI_MARKET_JOBS_ENABLED=false`. La stack de vérification
  (`npm run stack:start`) les coupe toujours.
- Arrêter la base : `docker compose -f infra/docker-compose.yml stop` (Spring ne l'arrête pas,
  `lifecycle-management=start-only`). Repartir de zéro : `... down -v` (supprime les données).

## Vérifier l'application réelle (agent `app-verifier`)

```bash
npm run stack:start    # base jetable + backend :18080 + frontend http://localhost:14200
npm run stack:status   # rappelle l'URL et l'identifiant
npm run stack:stop     # arrête tout, supprime la base jetable et les snapshots du navigateur
```

- Copie **isolée** de l'app (projet Compose `investai-verify`, ports 15432 / 18080 / 14200) :
  elle peut tourner en même temps que la stack de développement, sans toucher à ses données ni
  à `infra/.env`. Identifiant `verifier` affiché au démarrage ; mot de passe aléatoire
  jamais affiché, à lire dans le champ `password` de `.local-stack/state.json` (ignoré par Git,
  avec les journaux).
- Dans Claude Code : `/verify-app <critères>` lance l'agent `app-verifier`, qui pilote un
  navigateur via le serveur MCP `investai-browser` (`.mcp.json` : Playwright MCP en version
  figée, sans interface, requêtes restreintes à `http://localhost:14200` : un garde-fou, pas une
  frontière de sécurité). Claude Code demande d'approuver ce serveur au premier lancement.
  Navigateur utilisé : Chrome installé (sinon `cd frontend && npx playwright install chrome`).
  Monter la version dans `.mcp.json` à la main (Dependabot ne la suit pas).

## Configuration GitHub

Déjà en place (dépôt public) :

- Ruleset **`protect-main`** (Settings → Rules → Rulesets) : PR obligatoire, historique linéaire,
  pas de force-push ni de suppression, merge squash/rebase, checks requis : `Backend verify`,
  `Frontend check`, `Frontend e2e`, `Secret scan (gitleaks)`, `CodeQL (*)`, `SAST (Semgrep)`,
  `Dependency vulnerabilities (OSV-Scanner)`, `Filesystem & IaC scan (Trivy)`,
  `Dependency review`, `Conventional PR title`, `Conventional commits`.
- **Secret scanning + push protection**, **Dependabot alerts + security updates**.

Reste à faire manuellement :

1. **SonarCloud** : créer l'organisation `theophanetsy` et le projet `theophanetsy_InvestAI` sur
   https://sonarcloud.io (import GitHub), désactiver l'« Automatic Analysis », puis ajouter le
   secret `SONAR_TOKEN` (Settings → Secrets and variables → Actions). Ajouter ensuite
   `SonarCloud quality gate` aux checks requis.
