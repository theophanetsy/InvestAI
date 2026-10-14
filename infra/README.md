# infra

Configuration d'exécution locale.

| Fichier | Rôle |
|---|---|
| `docker-compose.yml` | PostgreSQL 18 (loopback uniquement, volume `postgres-data`) |
| `.env.example` | Variables à copier dans `.env` (ignoré par Git) : mot de passe PostgreSQL, utilisateur InvestAI (`INVESTAI_USERNAME`, `INVESTAI_PASSWORD_HASH` produit par `node scripts/hash-password.mjs`) |

Démarrage : voir [docs/setup.md](../docs/setup.md#lancer-lapplication-en-local).

À venir : stack d'observabilité Grafana LGTM / OpenTelemetry et Langfuse (Phase 3), images
backend/frontend (Phase 10). Les Dockerfiles et manifestes ajoutés ici sont analysés par Trivy en CI.
