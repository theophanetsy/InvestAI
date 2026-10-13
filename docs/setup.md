# Installation du poste de dev

## Prérequis

| Outil | Version | Installation (Windows) |
|---|---|---|
| JDK Temurin | 25 | `winget install EclipseAdoptium.Temurin.25.JDK` puis définir `JAVA_HOME` |
| Node.js | 24 (`.nvmrc`) | https://nodejs.org ou `nvm` |
| gitleaks | ≥ 8.30 | `winget install Gitleaks.Gitleaks` (requis par le hook pre-commit) |
| Docker Desktop | récent | requis à partir de la Phase 1 (Postgres, Testcontainers, observabilité) |
| GitHub CLI | récent | `winget install GitHub.cli` |

Maven n'a pas besoin d'être installé : utiliser `backend/mvnw`.

## Premier lancement

```bash
npm install                 # installe Lefthook + commitlint et active les git hooks
cd frontend && npm ci && npx playwright install chromium
cd ../backend && ./mvnw verify
```

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
