# 0003 — Garde-fous qualité et sécurité dès la Phase 0

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

Le code sera majoritairement écrit par des coding agents. Sans garde-fous automatiques, la qualité
dérive (règles contournées, tests absents, failles). Les contrôles doivent être en place **avant**
le premier code métier et ne pas pouvoir être affaiblis silencieusement.

## Décision

Défense en profondeur, du poste de l'agent jusqu'au merge :

| Couche | Backend | Frontend | Transverse |
|---|---|---|---|
| Édition (hooks Claude Code) | Spotless auto, blocage des configs | Prettier auto, blocage des configs | Blocage des marqueurs de suppression, check en fin de tour |
| Commit (Lefthook) | Spotless, Checkstyle | Prettier, ESLint, Stylelint | gitleaks, commitlint |
| Build | Error Prone + NullAway, Checkstyle, PMD, SpotBugs + FindSecBugs, ArchUnit, JaCoCo ≥ 80/70, Enforcer | TS strict + strictTemplates, ESLint strict-type-checked + angular-eslint all, Stylelint, Vitest ≥ 80/70, Playwright | — |
| CI | `ci-backend` | `ci-frontend` (+ `npm audit` prod) | CodeQL, Semgrep (+ règles projet), gitleaks, OSV-Scanner, Trivy, dependency review, SonarCloud, PR conventions, PIT nightly |
| GitHub | — | — | Protection de `main`, CODEOWNERS, Dependabot, actions épinglées par SHA |

- OWASP Dependency-Check n'est pas utilisé (lent, nécessite une clé NVD) : OSV-Scanner +
  Dependabot + Trivy couvrent la SCA.
- Les fichiers de config qualité sont protégés par un hook `PreToolUse` : un agent ne peut ni les
  modifier ni ajouter de marqueur de suppression ; seul l'utilisateur le peut.
- Toute exception de sécurité est tracée dans `docs/security/accepted-risks.md`.

## Conséquences

- ➕ Qualité homogène quel que soit l'auteur ; échec rapide (local) avant la CI.
- ➖ Build plus long (~1 min backend), courbe d'apprentissage des règles.
- ➖ Le hook ne couvre que les outils d'édition : un agent pourrait modifier un fichier via le
  shell. CODEOWNERS + revue de PR restent le dernier rempart.
- Suivi : ajuster les règles trop bruyantes par ADR, jamais en les désactivant inline.
