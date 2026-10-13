# Risques de sécurité acceptés

Toute vulnérabilité ignorée dans un outil (OSV-Scanner, Trivy, npm audit, SpotBugs…) doit figurer
ici avec une justification et une date de réévaluation. Les entrées expirées bloquent la CI
(`ignoreUntil`).

| ID | Composant | Advisory | Portée | Justification | Réévaluer le |
|---|---|---|---|---|---|
| AR-001 | `braces@3.0.3` via `stylelint` (frontend, dev) | [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) — DoS par motifs glob imbriqués | Outil de lint local/CI uniquement, jamais embarqué dans le bundle | Aucune version corrigée publiée ; l'entrée est un glob contrôlé par le dépôt, pas par un attaquant. `npm audit --omit=dev` (bloquant) n'est pas concerné. | 2027-01-13 |
