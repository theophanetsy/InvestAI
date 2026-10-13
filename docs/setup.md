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
cp infra/.env.example infra/.env     # puis renseigner POSTGRES_PASSWORD (fichier ignoré par Git)
cd backend && ./mvnw spring-boot:run # profil local : démarre PostgreSQL via Docker Compose
cd frontend && npm start             # http://localhost:4200 (proxy /api → :8080)
```

- Santé : http://localhost:8080/actuator/health/readiness (inclut la base).
- Arrêter la base : `docker compose -f infra/docker-compose.yml stop` (Spring ne l'arrête pas,
  `lifecycle-management=start-only`). Repartir de zéro : `... down -v` (supprime les données).

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
