# Environnements des coding agents (sandbox, cloud, devcontainer)

Un agent qui travaille hors de ton poste doit pouvoir **construire et tester** le projet avant
d'ouvrir une PR. Source unique d'installation : [`scripts/setup-dev-env.sh`](../scripts/setup-dev-env.sh).

## Ce que fait le script

Linux x64/arm64, idempotent, sans sudo (outils dans `~/.local`), chaque téléchargement vérifié par
SHA-256 :

| Étape | Détail |
|---|---|
| Node.js 24 | depuis nodejs.org (si absent ou mauvaise version) |
| JDK Temurin 25 | depuis l'API Adoptium (si absent ou mauvaise version) |
| gitleaks 8.30.1 | requis par le hook pre-commit (sinon tout commit est bloqué) |
| Dépendances | `npm ci` racine (Lefthook, commitlint) et `frontend/` |
| Chromium | pour Playwright (`--skip-browsers` pour l'ignorer) |
| Cache Maven | pré-téléchargement des dépendances et plugins (`--skip-warmup` pour l'ignorer) |
| Docker | détecté et signalé (non installé par le script) |

Variables d'environnement écrites dans `~/.investai-env`, chargé par `~/.bashrc` / `~/.profile`
(et ajouté à `CLAUDE_ENV_FILE` s'il existe). Durée : ~3 min sur une machine vierge.

## Brancher le script selon l'environnement

| Environnement | Configuration |
|---|---|
| **Claude Code sur le web** | Paramètres de l'environnement → script de configuration : `bash scripts/setup-dev-env.sh`. Accès réseau requis (voir ci-dessous). |
| **GitHub Codespaces / VS Code Dev Containers** | Automatique via `.devcontainer/devcontainer.json` (inclut Docker-in-Docker : suite complète). |
| **Autre agent cloud** (Copilot, Codex…) | Lancer `bash scripts/setup-dev-env.sh` dans son étape de setup. Pour Copilot : workflow `.github/workflows/copilot-setup-steps.yml` à ajouter le moment venu. |

Domaines réseau nécessaires : `nodejs.org`, `api.adoptium.net`, `github.com` (+ `objects.githubusercontent.com`),
`registry.npmjs.org`, `repo.maven.apache.org`, `cdn.playwright.dev` / `playwright.download.prss.microsoft.com`,
et `registry-1.docker.io` si Docker est disponible.

## Sans Docker : mode dégradé

Les tests d'intégration (`*IT`) utilisent Testcontainers et **exigent Docker**. Si Docker est
absent, l'agent lance :

```bash
cd backend && ./mvnw -DskipITs verify   # tout sauf les tests d'intégration
cd frontend && npm run check && npm run e2e
```

et **écrit explicitement dans la PR que les tests d'intégration n'ont pas été exécutés localement**.
La CI les exécute toujours (check requis `Backend verify`) : rien n'est mergé sans eux.

On n'utilise pas `@Testcontainers(disabledWithoutDocker = true)` : un test sauté en silence
ressemble à un test qui passe.
