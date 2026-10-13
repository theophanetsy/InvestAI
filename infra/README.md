# infra

Configuration d'exécution locale.

| Fichier | Rôle |
|---|---|
| `docker-compose.yml` | PostgreSQL 18 (loopback uniquement, volume `postgres-data`) |
| `.env.example` | Variables à copier dans `.env` (ignoré par Git) |

Démarrage : voir [docs/setup.md](../docs/setup.md#lancer-lapplication-en-local).

À venir : stack d'observabilité Grafana LGTM / OpenTelemetry (Phase 1.6), Langfuse (Phase 3),
conteneurs backend/frontend. Les Dockerfiles et manifestes ajoutés ici sont analysés par Trivy en CI.
